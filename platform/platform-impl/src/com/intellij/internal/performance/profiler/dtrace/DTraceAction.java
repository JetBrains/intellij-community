/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.performance.profiler.dtrace;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.credentialStore.OneTimeString;
import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.process.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

public class DTraceAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.internal.performance.profiler.dtrace");

  private static final String DTRACE_SUDO_KEY = "DTRACE_SUDO_KEY";

  private static CredentialAttributes getSudoCredAttr() {
    return new CredentialAttributes(DTRACE_SUDO_KEY,  SystemProperties.getUserName());
  }

  @Nullable
  private static char[] getCredentials() {
    Credentials cred = PasswordSafe.getInstance().get(getSudoCredAttr());
    if (cred == null) return null;
    final OneTimeString password = cred.getPassword();
    return password != null ? password.toCharArray() : null;
  }


  private static void saveCredentials(char[] password) {
    PasswordSafe.getInstance().set(getSudoCredAttr(), new Credentials(SystemProperties.getUserName(), password), false);
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    if (SystemInfo.isMac) {
      (new DTraceWindow()).setVisible(true);
    }
  }


  public static class DTraceWindow extends JDialog {


    private JTextArea outputArea;
    private JTextField pidField;
    private volatile boolean isRunning = false;
    private OSProcessHandler myProcessHandler;
    private OutputStream mySink;
    File tmpDir;
    String lwawtScr;
    Process p;
    volatile boolean outputData;
    char[] myPasswd;
    boolean rememberPasswd = false;

    public DTraceWindow() {
      super((Frame)null);
      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      setLayout(new GridBagLayout());

      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 0.1;
      c.gridx = 0;
      c.gridy = 0;

      pidField = new JTextField(4);
      pidField.setText(ApplicationManager.getApplicationPid());
      add(pidField, c);

      JPanel buttonPane = new JPanel(new GridLayout(1, 2));

      final JButton select = new JButton(AllIcons.Actions.FindPlain);
      select.addActionListener(e -> selectProcess());

      buttonPane.add(select);

      c.anchor = GridBagConstraints.WEST;
      c.gridx = 1;
      c.gridy = 0;
      c.fill = GridBagConstraints.VERTICAL;

      final JButton button = new JButton(AllIcons.General.Run);
      button.addActionListener(e -> {
        if (isRunning) {
          button.setIcon(AllIcons.General.Run);
          isRunning = false;
          stopTrace();
        }
        else {
          button.setIcon(AllIcons.Actions.Suspend);
          isRunning = true;
          startTrace();
        }
      });

      buttonPane.add(button);
      add(buttonPane, c);
      c.weightx = 1.0;
      c.gridx = 2;
      c.gridy = 0;
      c.anchor = GridBagConstraints.EAST;

      final JButton config = new JButton(AllIcons.General.Gear);
      config.addActionListener(e -> authorize(true));

      add(config, c);

      c.anchor = GridBagConstraints.CENTER;
      c.gridx = 0;
      c.gridy = 1;
      c.gridwidth = 4;
      c.fill = GridBagConstraints.BOTH;
      c.weighty = 1;
      outputArea = new JTextArea(30, 120);
      add(new JBScrollPane(outputArea), c);


      try {
        tmpDir = FileUtil.createTempDirectory("jtr", "scr", true);
        File f = new File(tmpDir, "lwawt.d");
        if (!f.createNewFile()) {
          outputArea.setText(f.getAbsolutePath() + " not created");
        }
        else {
          final InputStream resourceAsStream =
            getClass().getClassLoader().getResourceAsStream("com/intellij/internal/performance/profiler/dtrace/lwawt.d");
          final FileOutputStream outputStream = new FileOutputStream(f);

          try {
            FileUtil.copy(resourceAsStream, outputStream);
          }
          finally {
            StreamUtil.closeStream(resourceAsStream);
            StreamUtil.closeStream(outputStream);
          }
        }

        lwawtScr = f.getAbsolutePath();
      }
      catch (IOException e) {
        LOG.info(e);
      }
      pack();
    }


    @Nullable
    private static SudoAuthDialog showAuthDialog() {
      final Ref<SudoAuthDialog> dialog = Ref.create();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        char[] cred = getCredentials();
        dialog.set(new SudoAuthDialog());
        dialog.get().setup(cred != null ? cred : new char[]{});
        dialog.get().showAndGet();
      }, ModalityState.any());
      return dialog.get();
    }


    private void startTrace() {
      try {
        outputData = false;


        if (!authorize(false)) return;

        PtyCommandLine dtraceCmd = new PtyCommandLine();
        dtraceCmd.setExePath("sudo");
        dtraceCmd.addParameter("--reset-timestamp");
        dtraceCmd.addParameter("dtrace");
        dtraceCmd.addParameter("-q");
        dtraceCmd.addParameter("-p");
        dtraceCmd.addParameter(pidField.getText());
        dtraceCmd.addParameter("-s");
        dtraceCmd.addParameter(lwawtScr);

        p = dtraceCmd
          .startProcessWithPty(
            CommandLineUtil.toCommandLine(dtraceCmd.getExePath(), dtraceCmd.getParametersList().getList()), false);
        myProcessHandler = new OSProcessHandler(p, dtraceCmd.toString());
        myProcessHandler.setHasPty(true);
        myProcessHandler.addProcessListener(new ProcessAdapter() {
          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            if (outputType == ProcessOutputTypes.STDOUT) {
              if (event.getText().contains("Sorry, try again.")) {
                authorize(true);
                try {
                  mySink.write((String.copyValueOf(myPasswd) + "\n").getBytes(Charset.forName("UTF-8")));
                  mySink.flush();
                }
                catch (IOException e) {
                  LOG.info(e);
                }
              }
              else if (event.getText().contains("</data>")) {
                outputData = false;
              }
              else if (!event.getText().contains("<data>")) {
                if (rememberPasswd) {
                  saveCredentials(myPasswd);
                }
                if (outputData) {
                  outputArea.setText(outputArea.getText() + event.getText());
                }
              }
              else {
                outputData = true;
                outputArea.setText("");
              }
            }
            else if (outputType == ProcessOutputTypes.STDERR) {
              LOG.info("STDERR: " + event.getText());
            }
          }
        });

        mySink = myProcessHandler.getProcessInput();

        mySink.write((String.copyValueOf(myPasswd) + "\n").getBytes(Charset.forName("UTF-8")));
        mySink.flush();
      }
      catch (IOException e) {
        LOG.info(e);
      }

      myProcessHandler.startNotify();
    }

    private boolean authorize(boolean reset) {
      char[] cred = getCredentials();
      if (cred == null || reset) {

        SudoAuthDialog dialog = showAuthDialog();

        if (dialog == null || !dialog.isOK()) {
          return false;
        }

        myPasswd = dialog.getPassword();
        rememberPasswd = dialog.isRememberPassword();
        if (rememberPasswd) {
          saveCredentials(myPasswd);
        }
      }
      else {
        myPasswd = cred;
      }
      return true;
    }

    final SpeedSearchFilter<ProcessInfo> filter = new SpeedSearchFilter<ProcessInfo>() {
      @Override
      public boolean canBeHidden(ProcessInfo value) {
        return true;
      }

      @Override
      public String getIndexedString(ProcessInfo value) {
        return value.getPid() +
               " " +
               (value.getExecutableCannonicalPath().isPresent()
                ? value.getExecutableCannonicalPath().get()
                : value.getExecutableDisplayName());
      }
    };

    private void selectProcess() {
      final ProcessInfo[] processList = OSProcessUtil.getProcessList();

      ListPopupStep<ProcessInfo> step = new ListPopupStep<ProcessInfo>() {
        @NotNull
        @Override
        public List<ProcessInfo> getValues() {
          return Arrays.asList(processList);
        }

        @Override
        public boolean isSelectable(ProcessInfo value) {
          return true;
        }

        @Nullable
        @Override
        public Icon getIconFor(ProcessInfo aValue) {
          return null;
        }

        @NotNull
        @Override
        public String getTextFor(ProcessInfo value) {
          return value.getPid() +
                 " " +
                 (value.getExecutableCannonicalPath().isPresent()
                  ? value.getExecutableCannonicalPath().get()
                  : value.getExecutableDisplayName());
        }

        @Nullable
        @Override
        public ListSeparator getSeparatorAbove(ProcessInfo value) {
          return null;
        }

        @Override
        public int getDefaultOptionIndex() {
          return 0;
        }

        @Nullable
        @Override
        public String getTitle() {
          return null;
        }

        @Nullable
        @Override
        public PopupStep onChosen(ProcessInfo selectedValue, boolean finalChoice) {
          return null;
        }

        @Override
        public boolean hasSubstep(ProcessInfo selectedValue) {
          return false;
        }

        @Override
        public void canceled() {

        }

        @Override
        public boolean isMnemonicsNavigationEnabled() {
          return false;
        }

        @Nullable
        @Override
        public MnemonicNavigationFilter<ProcessInfo> getMnemonicNavigationFilter() {
          return null;
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Nullable
        @Override
        public SpeedSearchFilter<ProcessInfo> getSpeedSearchFilter() {
          return filter;
        }

        @Override
        public boolean isAutoSelectionEnabled() {
          return false;
        }

        @Nullable
        @Override
        public Runnable getFinalRunnable() {
          return null;
        }
      };

      final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
      final JList mainList = ((ListPopupImpl)popup).getList();

      ListSelectionListener listener = event -> {
        if (event.getValueIsAdjusting()) return;

        Object item = ((JList)event.getSource()).getSelectedValue();
        pidField.setText(Integer.toString(((ProcessInfo)item).getPid()));
      };
      popup.addListSelectionListener(listener);

      // force first valueChanged event
      listener.valueChanged(new ListSelectionEvent(mainList, mainList.getMinSelectionIndex(), mainList.getMaxSelectionIndex(), false));

      popup.showInFocusCenter();
    }

    private void stopTrace() {
      try {
        mySink.write(3);
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }


    public void setPassword(char[] password) {
      myPasswd = password;
    }
  }
}
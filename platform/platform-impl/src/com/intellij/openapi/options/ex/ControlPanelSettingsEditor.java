/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.options.ex;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.ui.search.DefaultSearchableConfigurable;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LabeledIcon;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 8, 2003
 * Time: 9:40:01 PM
 * To change this template use Options | File Templates.
 */
public class ControlPanelSettingsEditor extends DialogWrapper {
  private static final int ICONS_PER_ROW = 8;
  private static final Insets ICON_INSETS = new Insets(2, 2, 2, 2);

  protected final Project myProject;
  private final ConfigurableGroup[] myGroups;
  private JPanel myPanel;

  private Configurable myKeypressedConfigurable = null;
  private int mySelectedRow = 0;
  private int mySelectedColumn = 0;
  private int mySelectedGroup = 0;

  private Set<Configurable> myOptionContainers = null;
  private SearchUtil.ConfigurableSearchTextField mySearchField;
  private final Alarm mySearchUpdater = new Alarm();
  private final JBPopup[] myPopup = new JBPopup[2];

  public ControlPanelSettingsEditor(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    super(project, true);
    myProject = project;
    myGroups = groups;
    setTitle(OptionsBundle.message("settings.panel.title"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    init();
    if (preselectedConfigurable != null) {
      selectConfigurable(preselectedConfigurable);
      editConfigurable(preselectedConfigurable);
    }
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.options.ex.ControlPanelSettingsEditor";
  }

  protected Action[] createActions() {
    return new Action[]{getCancelAction()};
  }

  @NonNls
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("preferences");
  }

  protected Action[] createLeftSideActions() {
    return new Action[]{new SwitchToClassicViewAction()};
  }

  protected JComponent createCenterPanel() {
    myPanel = new JPanel(new VerticalFlowLayout());
    for (int i = 0; i < myGroups.length; i++) {
      ConfigurableGroup group = myGroups[i];
      myPanel.add(createGroupComponent(group, i));
    }

    final MyKeyAdapter keyAdapter = new MyKeyAdapter();
    myPanel.addKeyListener(keyAdapter);
    Disposer.register(myDisposable, new Disposable() {
      public void dispose() {
        myPanel.removeKeyListener(keyAdapter);
      }
    });
    
    JPanel panel = new JPanel(new GridBagLayout());
    panel.add(myPanel,
              new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                     new Insets(0, 0, 0, 0), 0, 0));


    JBScrollPane scrollPane = ScrollPaneFactory.createScrollPane(panel);
    scrollPane.setBorder(null);
    return scrollPane;
  }

  private void selectConfigurable(Configurable configurable) {
    for (int g = 0; g < myGroups.length; g++) {
      ConfigurableGroup group = myGroups[g];
      Configurable[] allConfigurables = group.getConfigurables();
      int count = allConfigurables.length;
      int rowCount = getRowCount(count);

      for (int i = 0; i < rowCount; i++) {
        for (int j = 0; j < ICONS_PER_ROW; j++) {
          int n = i * ICONS_PER_ROW + j;
          if (n < count && configurable == allConfigurables[n]) {
            mySelectedGroup = g;
            mySelectedRow = i;
            mySelectedColumn = j;
            return;
          }
        }
      }
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel;
  }

  private JComponent createGroupComponent(ConfigurableGroup group, int groupIdx) {
    JPanel panel = new JPanel(new VerticalFlowLayout());

    final TitledSeparator separator = new TitledSeparator();
    separator.setText(group.getDisplayName());
    separator.setTitleFont(new JLabel().getFont().deriveFont(20.0f));
    panel.add(separator);

    Configurable[] allConfigurables = group.getConfigurables();
    int count = allConfigurables.length;
    int rowCount = getRowCount(count);
    JPanel toolBar = new JPanel(new GridLayout(0, ICONS_PER_ROW));

    for (int i = 0; i < rowCount; i++) {
      for (int j = 0; j < ICONS_PER_ROW; j++) {
        int n = i * ICONS_PER_ROW + j;
        if (n < count) {
          toolBar.add(createActionButton(allConfigurables[n], getShortcut(n, groupIdx), groupIdx, j, i));
        }
      }
    }

    panel.add(toolBar);

    panel.add(new JPanel());
    return panel;
  }

  private static int getRowCount(int count) {
    return count / ICONS_PER_ROW + (count % ICONS_PER_ROW == 0 ? 0 : 1);
  }

  private static KeyStroke getShortcut(int actionIdx, int groupIdx) {
    int mnemonic = getMnemonicByIndex(actionIdx, groupIdx);
    if (mnemonic == 0) return null;
    return KeyStroke.getKeyStroke(mnemonic, 0);
  }

  private static int getMnemonicByIndex(int idx, int groupIdx) {
    if (groupIdx == 0) {
      if (idx >= 0 && idx < 9) return KeyEvent.VK_1 + idx;
      if (idx == 9) return KeyEvent.VK_0;
      return 0;
    }

    if (groupIdx == 1) {
      if (idx >= 0 && idx < KeyEvent.VK_Z - KeyEvent.VK_A) return KeyEvent.VK_A + idx;
    }

    return 0;
  }

  @Nullable
  private Configurable getSelectedConfigurable() {
    if (mySelectedColumn == -1 || mySelectedRow == -1 || mySelectedGroup == -1) return null;
    final Configurable[] configurables = myGroups[mySelectedGroup].getConfigurables();
    final int index = mySelectedColumn + mySelectedRow * ICONS_PER_ROW;
    if (index >= configurables.length) return null;
    return configurables[index];
  }

  private JComponent createActionButton(final Configurable configurable,
                                        KeyStroke shortcut,
                                        final int groupIdx,
                                        final int column,
                                        final int row) {
    return new MyActionButton(configurable, shortcut, groupIdx, row, column);
  }

  private void editConfigurable(Configurable configurable) {
    Configurable actualConfigurable = configurable;
    if (configurable instanceof ProjectComponent) {
      actualConfigurable = new ProjectConfigurableWrapper(myProject, configurable);
    }

    if (actualConfigurable instanceof SearchableConfigurable){
      actualConfigurable = new DefaultSearchableConfigurable((SearchableConfigurable)actualConfigurable);
      ((DefaultSearchableConfigurable)actualConfigurable).clearSearch();
      @NonNls final String filter = mySearchField.getText();
      if (filter != null && filter.length() > 0 ){
        final DefaultSearchableConfigurable finalConfigurable = (DefaultSearchableConfigurable)actualConfigurable;
        SwingUtilities.invokeLater(new Runnable (){
          public void run() {
            finalConfigurable.enableSearch(filter);
          }
        });
      }
    }
    final SingleConfigurableEditor configurableEditor =
      new SingleConfigurableEditor(myProject, actualConfigurable, SingleConfigurableEditor.createDimensionKey(configurable));
    configurableEditor.show();
  }


  public void dispose() {
    for (JBPopup popup : myPopup) {
      if (popup != null) {
        popup.cancel();
      }
    }

    mySearchUpdater.cancelAllRequests();
    myOptionContainers = null;
    super.dispose();
  }

  protected JComponent createNorthPanel() {
    final Consumer<String> selectConfigurable = new Consumer<String>() {
      public void consume(final String configurableId) {
        if (myOptionContainers != null) {
          for (Configurable configurable : myOptionContainers) {
            if (Comparing.strEqual(configurable.getDisplayName(), configurableId)){
              editConfigurable(configurable);
              return;
            }
          }
        }
      }
    };
    final SearchableOptionsRegistrar optionsRegistrar = SearchableOptionsRegistrar.getInstance();
    final JPanel panel = new JPanel(new GridBagLayout());
    mySearchField = new SearchUtil.ConfigurableSearchTextField();
    final DocumentAdapter docAdapter = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        mySearchUpdater.cancelAllRequests();
        mySearchUpdater.addRequest(new Runnable() {
          public void run() {
            @NonNls final String searchPattern = mySearchField.getText();
            if (searchPattern != null && searchPattern.length() > 0) {
              myOptionContainers = optionsRegistrar.getConfigurables(myGroups, e.getType(), myOptionContainers, searchPattern,
                                                                     myProject).getContentHits();
            }
            else {
              myOptionContainers = null;
            }
            SearchUtil.showHintPopup(mySearchField, myPopup, mySearchUpdater, selectConfigurable, myProject);
            myPanel.repaint();
          }
        }, 300, ModalityState.defaultModalityState());
      }
    };
    mySearchField.addDocumentListener(docAdapter);
    Disposer.register(myDisposable, new Disposable() {
      public void dispose() {
        if (mySearchField != null) {
          panel.remove(mySearchField);
          mySearchField.removeDocumentListener(docAdapter);
          mySearchField = null;
        }
      }
    });

    SearchUtil.registerKeyboardNavigation(mySearchField, myPopup, mySearchUpdater, selectConfigurable, myProject);
    final GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    panel.add(Box.createHorizontalBox(), gc);

    gc.gridx++;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    final JLabel label = new JLabel(IdeBundle.message("search.textfield.title"));
    panel.add(label, gc);
    label.setLabelFor(mySearchField);

    gc.gridx++;
    final int height = mySearchField.getPreferredSize().height;
    mySearchField.setPreferredSize(new Dimension(100, height));
    panel.add(mySearchField, gc);
    return panel;
  }

  private class MyActionButton extends JComponent implements ActionButtonComponent {
    private final Configurable myConfigurable;
    private final int myGroupIdx;
    private final int myRowIdx;
    private final int myColumnIdx;
    private boolean myIsMouseInside = false;
    private final Icon myIcon;
    private final KeyStroke myShortcut;

    public MyActionButton(Configurable configurable, KeyStroke shortcut, int groupIdx, int rowIdx, int columnIdx) {
      myConfigurable = configurable;
      myGroupIdx = groupIdx;
      myRowIdx = rowIdx;
      myColumnIdx = columnIdx;
      myShortcut = shortcut;
      myIcon = createIcon();
      setToolTipText(null);
      setupListeners();
    }

    private Icon createIcon() {
      Icon icon = myConfigurable.getIcon();
      if (icon == null) {
        icon = IconLoader.getIcon("/general/configurableDefault.png");
      }

      String displayName = myConfigurable.getDisplayName();

      return new LabeledIcon(icon, displayName, myShortcut == null ? null : " (" + KeyEvent.getKeyText(myShortcut.getKeyCode()) + ")");
    }

    public Dimension getPreferredSize() {
      return new Dimension(myIcon.getIconWidth() + ICON_INSETS.left + ICON_INSETS.right,
                           myIcon.getIconHeight() + ICON_INSETS.top + ICON_INSETS.bottom);
    }

    protected void paintComponent(Graphics g) {
      UIUtil.applyRenderingHints(g);
      super.paintComponent(g);
      ActionButtonLook look = ActionButtonLook.IDEA_LOOK;
      look.paintBackground(g, this);
      look.paintIcon(g, this, myIcon);
      look.paintBorder(g, this);
    }

    public int getPopState() {
      if (myKeypressedConfigurable == myConfigurable) return myIsMouseInside ? ActionButtonComponent.PUSHED : ActionButtonComponent.POPPED;
      if (myKeypressedConfigurable != null) return ActionButtonComponent.NORMAL;
      Configurable selectedConfigurable = getSelectedConfigurable();
      if (selectedConfigurable == myConfigurable) return ActionButtonComponent.POPPED;
      return ActionButtonComponent.NORMAL;
    }

    private void setupListeners() {
      final MouseAdapter mouseAdapter = new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          myIsMouseInside = true;
          myKeypressedConfigurable = myConfigurable;
          myPanel.repaint();
        }

        public void mouseReleased(MouseEvent e) {
          if (myKeypressedConfigurable == myConfigurable && myIsMouseInside) {
            myKeypressedConfigurable = null;
            editConfigurable(myConfigurable);
          }
          else {
            myKeypressedConfigurable = null;
          }

          myIsMouseInside = false;
          myPanel.repaint();
        }


        public void mouseEntered(final MouseEvent e) {
          myIsMouseInside = true;
          myPanel.repaint();
        }

        public void mouseExited(final MouseEvent e) {
          myIsMouseInside = false;
          myPanel.repaint();
        }
      };

      addMouseListener(mouseAdapter);
      Disposer.register(myDisposable, new Disposable() {
        public void dispose() {
          removeMouseListener(mouseAdapter);
        }
      });


      final MouseMotionListener motionListener = new MouseMotionListener() {
        public void mouseDragged(MouseEvent e) {
        }

        public void mouseMoved(MouseEvent e) {
          mySelectedColumn = myColumnIdx;
          mySelectedRow = myRowIdx;
          mySelectedGroup = myGroupIdx;
          myPanel.repaint();
        }
      };

      addMouseMotionListener(motionListener);
      Disposer.register(myDisposable, new Disposable() {
        public void dispose() {
          removeMouseMotionListener(motionListener);
        }
      });
    }
  }

  private class SwitchToClassicViewAction extends AbstractAction {
    public SwitchToClassicViewAction() {
      putValue(Action.NAME, OptionsBundle.message("control.panel.classic.view.button"));
    }

    public void actionPerformed(ActionEvent e) {
      close(OK_EXIT_CODE);

      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            ShowSettingsUtilImpl.showExplorerOptions(myProject, myGroups);
          }
        }, ModalityState.NON_MODAL);
    }
  }

  private class MyKeyAdapter extends KeyAdapter {
    public void keyPressed(KeyEvent e) {
      try {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          myKeypressedConfigurable = getSelectedConfigurable();
          return;
        }

        int code = e.getKeyCode();
        if (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN || code == KeyEvent.VK_RIGHT ||
            code == KeyEvent.VK_LEFT) {
          if (getSelectedConfigurable() == null) {
            mySelectedColumn = 0;
            mySelectedRow = 0;
            mySelectedGroup = 0;
            return;
          }

          int xShift = 0;
          int yShift = 0;

          if (code == KeyEvent.VK_UP) {
            yShift = -1;
          }
          else if (code == KeyEvent.VK_DOWN) {
            yShift = 1;
          }
          else if (code == KeyEvent.VK_LEFT) {
            xShift = -1;
          }
          else /*if (code == KeyEvent.VK_RIGHT)*/ {
            xShift = 1;
          }

          int newColumn = mySelectedColumn + xShift;
          int newRow = mySelectedRow + yShift;
          int newGroup = mySelectedGroup;

          if (newColumn < 0) newColumn = 0;
          if (newColumn >= ICONS_PER_ROW) newColumn = ICONS_PER_ROW - 1;

          int idx = newColumn + newRow * ICONS_PER_ROW;
          if (idx >= myGroups[newGroup].getConfigurables().length) {
            if (yShift > 0) {
              newRow = 0;
              newGroup++;
              if (newGroup >= myGroups.length) return;

              idx = newColumn + newRow * ICONS_PER_ROW;
              if (idx >= myGroups[newGroup].getConfigurables().length) return;
            }
            else if (xShift > 0) {
              return;
            }
          }

          if (yShift < 0 && idx < 0) {
            newGroup--;
            if (newGroup < 0) return;
            int rowCount = getRowCount(myGroups[newGroup].getConfigurables().length);
            newRow = rowCount - 1;
            idx = newColumn + newRow * ICONS_PER_ROW;
            if (idx >= myGroups[newGroup].getConfigurables().length) {
              if (newRow <= 0) return;
              newRow--;
            }
          }

          mySelectedColumn = newColumn;
          mySelectedRow = newRow;
          mySelectedGroup = newGroup;
          return;
        }

        myKeypressedConfigurable = ControlPanelMnemonicsUtil.getConfigurableFromMnemonic(e, myGroups);
      }
      finally {
        myPanel.repaint();
      }
    }

    public void keyReleased(KeyEvent e) {
      if (myKeypressedConfigurable != null) {
        e.consume();
        selectConfigurable(myKeypressedConfigurable);
        editConfigurable(myKeypressedConfigurable);
        myKeypressedConfigurable = null;
        myPanel.repaint();
      }
    }
  }
}

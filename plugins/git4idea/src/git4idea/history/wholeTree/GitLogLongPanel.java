/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.CompoundNumber;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import git4idea.history.browser.ChangesFilter;
import git4idea.history.browser.GitCommit;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author irengrig
 */
public class GitLogLongPanel {
  private final static String LOADING_CARD = "LOADING_CARD";
  private final static String READY_CARD = "READY_CARD";

  private final JPanel myPanel;

  private CardLayout myLayout;
  private final Loader myLoader;
  private final LinesProxy myLinesProxy;
  private UIRefresh myUIRefresh;
  private BigTableTableModel myTableModel;
  private JTextField myFilterField;
  private String myPreviousFilter;
  private JLabel myLoading;

  // todo for the case when hierarchy is also drawn?
  public GitLogLongPanel(final Project project, final Collection<VirtualFile> roots) {
    final LoaderImpl loaderImpl = new LoaderImpl(project, roots);
    myLoader = loaderImpl;
    myLinesProxy = loaderImpl.getLinesProxy();
    myLayout = new CardLayout();
    myPanel = new JPanel(myLayout);
    //LoaderImpl
    myUIRefresh = new UIRefresh() {
      @Override
      public void fireDataReady(int idxFrom, int idxTo) {
        myTableModel.fireTableRowsInserted(idxFrom, idxTo);
        myPanel.revalidate();
        myPanel.repaint();
      }
      @Override
      public void setLoadingShowNoDataState() {
        myLayout.show(myPanel, LOADING_CARD);
        myPanel.revalidate();
        myPanel.repaint();
      }
      @Override
      public void setSomeDataReadyState() {
        myLoading.setVisible(true);
        myLayout.show(myPanel, READY_CARD);
        myTableModel.fireTableDataChanged();
        myPanel.revalidate();
        myPanel.repaint();
      }

      @Override
      public void skeletonLoadComplete() {
        myLoading.setVisible(false);
        myPanel.revalidate();
        myPanel.repaint();
      }

      @Override
      public void acceptException(Exception e) {
        // TODO
        e.printStackTrace();
      }
    };
    loaderImpl.setUIRefresh(myUIRefresh);

    initPanel();
  }

  private void initPanel() {
    final JPanel container = new JPanel(new BorderLayout());
    final JPanel menu = new JPanel();
    final BoxLayout layout = new BoxLayout(menu, BoxLayout.X_AXIS);
    menu.setLayout(layout);

    myLoading = new JLabel("Loading");
    menu.add(myLoading);
    myFilterField = new JTextField(100);
    menu.add(myFilterField);
    createFilterFieldListener();

    myTableModel = new BigTableTableModel(Arrays.<ColumnInfo>asList(COMMENT, AUTHOR, DATE), myLinesProxy);
    final JBTable table = new JBTable(myTableModel);
    table.setModel(myTableModel);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(table);
    scrollPane.getViewport().addChangeListener(new MyChangeListener());

    container.add(menu, BorderLayout.NORTH);
    container.add(scrollPane, BorderLayout.CENTER);

    myPanel.add(LOADING_CARD, new JLabel("Loading"));
    myPanel.add(READY_CARD, container);

    table.setDefaultRenderer(Object.class, new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        final CompoundNumber memberData = ((LoaderImpl) myLoader).getTreeComposite().getMemberData(row);
        append(value.toString(), new SimpleTextAttributes(memberData.getMemberNumber() < colors.length ? colors[memberData.getMemberNumber()] :
                                                          UIUtil.getTableBackground(),
                                                          selected ? UIUtil.getTableSelectionForeground() : UIUtil.getTableForeground(),
                                                          null, SimpleTextAttributes.STYLE_PLAIN));
      }
    });
  }

  private final static Color[] colors = {Color.gray.brighter(), Color.green.brighter(), Color.orange.brighter()};

  private void createFilterFieldListener() {
    myFilterField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          checkIfFilterChanged();
        }
      }
    });
    myFilterField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        checkIfFilterChanged();
      }
    });
  }

  private void checkIfFilterChanged() {
    final String newValue = myFilterField.getText().trim();
    if (! Comparing.equal(myPreviousFilter, newValue)) {
      myPreviousFilter = newValue;
      getUIRefresh().setLoadingShowNoDataState();

      if (StringUtil.isEmptyOrSpaces(myPreviousFilter)) {
        // todo hierarchy presence can be determined here
        myLoader.loadSkeleton(Collections.<String>emptyList(), Collections.<ChangesFilter.Filter>emptyList());
      } else {
        // todo add starting points when ready
        // todo filters are also temporal
        myLoader.loadSkeleton(Collections.<String>emptyList(), Collections.<ChangesFilter.Filter>singletonList(new ChangesFilter.Author(myPreviousFilter)));
      }
    }
  }

  public static void showDialog(final Project project) {
    final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(GitVcs.getInstance(project));
    final GitLogLongPanel gitLogLongPanel = new GitLogLongPanel(project, Arrays.asList(roots));
    new MyDialog(project, gitLogLongPanel).show();
  }

  public UIRefresh getUIRefresh() {
    return myUIRefresh;
  }

  private final static ColumnInfo<Object, String> COMMENT = new ColumnInfo<Object, String>("Comment") {
    @Override
    public String valueOf(Object o) {
      if (o instanceof GitCommit) {
        return ((GitCommit) o).getDescription();
      }
      return o == null ? "" : o.toString();
    }
  };
  private final static ColumnInfo<Object, String> AUTHOR = new ColumnInfo<Object, String>("Committer") {
    @Override
    public String valueOf(Object o) {
      if (o instanceof GitCommit) {
        return ((GitCommit) o).getCommitter();
      }
      return o == null ? "" : o.toString();
    }
  };
  private final static ColumnInfo<Object, String> DATE = new ColumnInfo<Object, String>("Date") {
    @Override
    public String valueOf(Object o) {
      if (o instanceof GitCommit) {
        return ((GitCommit) o).getDate().toString();
      }
      return o == null ? "" : o.toString();
    }
  };

  // UIRefresh and Loader both share the same LinesProxy, and [result] data are actually passed through it

  public interface UIRefresh {
    @CalledInAwt
    void setLoadingShowNoDataState();
    @CalledInAwt
    void setSomeDataReadyState();
    @CalledInAwt
    void skeletonLoadComplete();
    @CalledInAwt
    void fireDataReady(final int idxFrom, final int idxTo);
    void acceptException(final Exception e);
  }

  private static class MyDialog extends DialogWrapper {
    private final GitLogLongPanel myGitLogLongPanel;

    private MyDialog(Project project, final GitLogLongPanel gitLogLongPanel) {
      super(project, true);
      myGitLogLongPanel = gitLogLongPanel;
      init();
    }

    @Override
    protected JComponent createCenterPanel() {
      return myGitLogLongPanel.myPanel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      myGitLogLongPanel.setModalityState(ModalityState.current());
      return super.getPreferredFocusedComponent();
    }
  }

  private void setModalityState(ModalityState current) {
    ((LoaderImpl) myLoader).setModalityState(current);
    checkIfFilterChanged();
  }

  private static class MyChangeListener implements ChangeListener {
    @Override
    public void stateChanged(ChangeEvent e) {
      System.out.println(e.toString());
    }
  }
}

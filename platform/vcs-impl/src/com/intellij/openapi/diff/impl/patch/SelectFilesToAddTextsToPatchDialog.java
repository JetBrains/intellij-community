/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 9/15/11
 * Time: 5:30 PM
 */
public class SelectFilesToAddTextsToPatchDialog extends DialogWrapper {
  private final Set<String> myBigFiles;
  private final ChangesBrowser myBrowser;
  private JLabel myWarningText;
  private JLabel myIncludedText;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.patch.SelectFilesToAddTextsToPatchDialog");
  private final int myTotalSize;
  private JPanel myPanel;
  private final Runnable myInclusionListener;

  public SelectFilesToAddTextsToPatchDialog(final Project project, final List<Change> changes, final Collection<Change> selectedChanges) {
    super(project, true);
    setTitle("Select file(s) to add their base revision text to patch");

    myTotalSize = changes.size();
    myBigFiles = new HashSet<String>();

    final Set<Change> exclude = getBig(changes);
    for (Change change : exclude) {
      myBigFiles.add(ChangesUtil.getFilePath(change).getPath());
    }

    myWarningText = new JLabel("There are big files selected, which increases patch size significantly");
    myWarningText.setIcon(UIUtil.getBalloonWarningIcon());
    myIncludedText = new JLabel();
    myInclusionListener = new Runnable() {
      @Override
      public void run() {
        final Collection<Change> includedChanges = myBrowser.getViewer().getIncludedChanges();
        myIncludedText
          .setText("Selected: " + (includedChanges.size() == myTotalSize ? "All" : ("" + includedChanges.size() + " of " + myTotalSize)));
        for (Change change : includedChanges) {
          if (myBigFiles.contains(ChangesUtil.getFilePath(change).getPath())) {
            myWarningText.setVisible(true);
            return;
          }
        }
        myWarningText.setVisible(false);
      }
    };
    myBrowser = new ChangesBrowser(project, null, changes, null, true, false, myInclusionListener, ChangesBrowser.MyUseCase.LOCAL_CHANGES, null);
    myBrowser.getViewer().setChangeDecorator(new ChangeNodeDecorator() {
      @Override
      public void decorate(Change change, SimpleColoredComponent component, boolean isShowFlatten) {
        String path = ChangesUtil.getFilePath(change).getPath();
        if (myBigFiles.contains(path)) {
          component.append(" ");
          component.append("File size is bigger than " + VcsConfiguration.ourMaximumFileForBaseRevisionSize / 1000 + "K",
                           SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
      }
      @Override
      public List<Pair<String, Stress>> stressPartsOfFileName(Change change, String parentPath) {
        return null;
      }
      @Override
      public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
      }
    });
    myBrowser.getViewer().setChangesToDisplay(changes);

    if (selectedChanges == null) {
      myBrowser.getViewer().excludeChanges(exclude);
    } else {
      myBrowser.getViewer().excludeChanges(changes);
      myBrowser.getViewer().includeChanges(selectedChanges);
    }
    myWarningText.setVisible(false);
    init();
    myInclusionListener.run();
  }

  public static Set<Change> getBig(List<Change> changes) {
    final Set<Change> exclude = new HashSet<Change>();
    for (Change change : changes) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision != null) {
        try {
          String content = beforeRevision.getContent();
          if (content.length() > VcsConfiguration.ourMaximumFileForBaseRevisionSize) {
            exclude.add(change);
          }
        }
        catch (VcsException e) {
          LOG.info(e);
        }
      }
    }
    return exclude;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myBrowser.getViewer();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "com.intellij.openapi.diff.impl.patch.SelectFilesToAddTextsToPatchDialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    myPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                                   new Insets(1, 1, 1, 1), 0, 0);
    myPanel.add(myBrowser, gb);
    gb.fill = GridBagConstraints.HORIZONTAL;
    ++ gb.gridy;
    gb.weighty = 0;
    myPanel.add(myWarningText, gb);
    ++ gb.gridy;
    myPanel.add(myIncludedText, gb);
    return myPanel;
  }
  
  public Collection<Change> getIncludedChanges() {
    return myBrowser.getViewer().getIncludedChanges();
  }
}

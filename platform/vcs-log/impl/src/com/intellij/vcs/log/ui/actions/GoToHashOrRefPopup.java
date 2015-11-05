/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.actions;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Function;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import com.intellij.vcsUtil.VcsImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class GoToHashOrRefPopup {
  private static final Logger LOG = Logger.getInstance(GoToHashOrRefPopup.class);

  @NotNull private final TextFieldWithProgress myTextField;
  @NotNull private final Function<String, Future> myOnSelectedHash;
  @NotNull private final Function<VcsRef, Future> myOnSelectedRef;
  @NotNull private final JBPopup myPopup;
  @Nullable private Future myFuture;
  @Nullable private VcsRef mySelectedRef;

  public GoToHashOrRefPopup(@NotNull final Project project,
                            @NotNull Collection<VcsRef> variants,
                            @NotNull Function<String, Future> onSelectedHash,
                            @NotNull Function<VcsRef, Future> onSelectedRef,
                            @NotNull VcsLogColorManager colorManager,
                            @NotNull Comparator<VcsRef> comparator) {
    myOnSelectedHash = onSelectedHash;
    myOnSelectedRef = onSelectedRef;
    myTextField = new TextFieldWithProgress<VcsRef>(project, new VcsRefCompletionProvider(project, variants, colorManager, comparator)) {
      @Override
      public void onOk() {
        if (myFuture == null) {
          final Future future = ((mySelectedRef == null || (!mySelectedRef.getName().equals(getText().trim())))
                                 ? myOnSelectedHash.fun(getText().trim())
                                 : myOnSelectedRef.fun(mySelectedRef));
          myFuture = future;
          showProgress();
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              try {
                future.get();
                okPopup();
              }
              catch (CancellationException ex) {
                cancelPopup();
              }
              catch (InterruptedException ex) {
                cancelPopup();
              }
              catch (ExecutionException ex) {
                LOG.error(ex);
                cancelPopup();
              }
            }
          });
        }
      }
    };
    myTextField.setAlignmentX(Component.LEFT_ALIGNMENT);

    JBLabel label = new JBLabel("Enter hash or branch/tag name:");
    label.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);

    JPanel panel = new JPanel();
    BoxLayout layout = new BoxLayout(panel, BoxLayout.PAGE_AXIS);
    panel.setLayout(layout);
    panel.add(label);
    panel.add(myTextField);
    panel.setBorder(new EmptyBorder(2, 2, 2, 2));

    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, myTextField.getPreferableFocusComponent())
      .setCancelOnClickOutside(true).setCancelOnWindowDeactivation(true).setCancelKeyEnabled(true).setRequestFocus(true).createPopup();
    myPopup.addListener(new JBPopupListener.Adapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        if (!event.isOk()) {
          if (myFuture != null) {
            myFuture.cancel(true);
          }
        }
        myFuture = null;
        myTextField.hideProgress();
      }
    });
  }

  private void cancelPopup() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myPopup.cancel();
      }
    });
  }

  private void okPopup() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myPopup.closeOk(null);
      }
    });
  }

  public void show(@NotNull JComponent anchor) {
    myPopup.showInCenterOf(anchor);
  }

  private class VcsRefCompletionProvider extends TextFieldWithAutoCompletionListProvider<VcsRef> {
    @NotNull private final Project myProject;
    @NotNull private final VcsLogColorManager myColorManager;
    @NotNull private final Comparator<VcsRef> myReferenceComparator;

    public VcsRefCompletionProvider(@NotNull Project project,
                                    @NotNull Collection<VcsRef> variants,
                                    @NotNull VcsLogColorManager colorManager,
                                    @NotNull Comparator<VcsRef> comparator) {
      super(variants);
      myProject = project;
      myColorManager = colorManager;
      myReferenceComparator = comparator;
    }

    @Override
    public LookupElementBuilder createLookupBuilder(@NotNull VcsRef item) {
      LookupElementBuilder lookupBuilder = super.createLookupBuilder(item);
      if (myColorManager.isMultipleRoots()) {
        lookupBuilder = lookupBuilder
          .withTypeText(getTypeText(item), new ColorIcon(15, VcsLogGraphTable.getRootBackgroundColor(item.getRoot(), myColorManager)),
                        true);
      }
      return lookupBuilder;
    }

    @Nullable
    @Override
    protected Icon getIcon(@NotNull VcsRef item) {
      return null;
    }

    @NotNull
    @Override
    protected String getLookupString(@NotNull VcsRef item) {
      return item.getName();
    }

    @Nullable
    @Override
    protected String getTailText(@NotNull VcsRef item) {
      if (!myColorManager.isMultipleRoots()) return null;
      return "";
    }

    @Nullable
    @Override
    protected String getTypeText(@NotNull VcsRef item) {
      if (!myColorManager.isMultipleRoots()) return null;
      return VcsImplUtil.getShortVcsRootName(myProject, item.getRoot());
    }

    @Override
    public int compare(VcsRef item1, VcsRef item2) {
      return myReferenceComparator.compare(item1, item2);
    }

    @Nullable
    @Override
    protected InsertHandler<LookupElement> createInsertHandler(@NotNull VcsRef item) {
      return new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
          mySelectedRef = (VcsRef)item.getObject();
          myTextField.onOk();
        }
      };
    }
  }
}

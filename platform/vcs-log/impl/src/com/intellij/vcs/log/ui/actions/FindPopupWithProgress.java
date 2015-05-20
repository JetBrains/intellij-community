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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class FindPopupWithProgress {
  private static final Logger LOG = Logger.getInstance(FindPopupWithProgress.class);

  @NotNull private final TextFieldWithProgress myTextField;
  @NotNull private final Function<String, Future> myFunction;
  @NotNull private final JBPopup myPopup;
  @Nullable private Future myFuture;

  public FindPopupWithProgress(@NotNull final Project project,
                               @NotNull Collection<String> variants,
                               @NotNull Function<String, Future> function) {
    myFunction = function;
    myTextField = new TextFieldWithProgress(project, variants) {
      @Override
      public void onOk() {
        if (myFuture == null) {
          final Future future = myFunction.fun(getText().trim());
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

    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myTextField, myTextField.getPreferableFocusComponent())
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

    final JBTextField field = new JBTextField(20);
    final Dimension size = field.getPreferredSize();
    final Insets insets = myTextField.getBorder().getBorderInsets(myTextField);
    size.height += 6 + insets.top + insets.bottom;
    size.width += 4 + insets.left + insets.right;
    myPopup.setSize(size);
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

  public void showUnderneathOf(@NotNull Component anchor) {
    myPopup.showUnderneathOf(anchor);
  }
}

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
package com.intellij.openapi.vcs.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.impl.ui.NotificationsUtil;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "GlobalMessageService",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/vcs.xml", scheme = StorageScheme.DIRECTORY_BASED)
  })
public class VcsGlobalMessageManager implements PersistentStateComponent<VcsGlobalMessage> {
  private VcsGlobalMessage myState;

  public static VcsGlobalMessageManager getInstance(final Project project) {
      return project.getComponent(VcsGlobalMessageManager.class);
  }

  @Nullable
  @Override
  public VcsGlobalMessage getState() {
    return myState;
  }

  @Override
  public void loadState(VcsGlobalMessage state) {
    myState = state == null ? new VcsGlobalMessage() : state;
  }

  @Nullable
  public JComponent getMessageBanner() {
    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      final VcsGlobalMessage message = myState;
      if (message != null && !StringUtil.isEmpty(message.message)) {
        final JEditorPane text = new JEditorPane();
        text.setEditorKit(UIUtil.getHTMLEditorKit());

        text.addHyperlinkListener(new HyperlinkListener() {
          @Override
          public void hyperlinkUpdate(HyperlinkEvent e) {
            if (HyperlinkEvent.EventType.ACTIVATED == e.getEventType()) BrowserUtil.browse(e.getURL());
          }
        });

        final JLabel label = new JLabel(NotificationsUtil.buildHtml("", message.message, null));
        text.setText(NotificationsUtil.buildHtml("", message.message, "width:" + Math.min(400, label.getPreferredSize().width) + "px;"));
        text.setEditable(false);
        text.setOpaque(false);

        text.setBorder(null);

        final JPanel content =
          new JPanel(new BorderLayout((int)(label.getIconTextGap() * 1.5), (int)(label.getIconTextGap() * 1.5)));

        text.setCaretPosition(0);
        JScrollPane pane = ScrollPaneFactory.createScrollPane(text,
                                                              ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pane.setBorder(null);
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        content.add(pane, BorderLayout.CENTER);

        final NonOpaquePanel north = new NonOpaquePanel(new BorderLayout());
        north.add(new JLabel(AllIcons.General.BalloonWarning), BorderLayout.NORTH);
        content.add(north, BorderLayout.WEST);

        content.setBorder(new EmptyBorder(8, 4, 8, 4));
        content.setBackground(MessageType.WARNING.getPopupBackground());
        return content;
      }
    }

    return null;
  }
}

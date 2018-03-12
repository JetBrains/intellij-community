/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.pullrequests.ui;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.data.GithubUser;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.List;

public class GithubAuthorAssigneesIconPanel extends JComponent {
  private static final int AUTHOR_SIZE = JBUI.scale(32);
  private static final int ASSIGNEE_SIZE = JBUI.scale(16);
  private static final int AUTHOR_BORDER = JBUI.scale(3);
  private static final int ASSIGNEE_BORDER = JBUI.scale(1);

  @NotNull private Icon myAuthorIcon;
  @NotNull private List<Icon> myAssigneeIcons;

  public GithubAuthorAssigneesIconPanel() {
    setUsers(null, Collections.emptyList());
  }

  public void setUsers(@Nullable GithubUser user, @NotNull List<GithubUser> assignees) {
    myAuthorIcon = GithubUtil.createAvatarIcon(user, AUTHOR_SIZE);
    myAssigneeIcons = ContainerUtil.map(assignees, it -> GithubUtil.createAvatarIcon(it, ASSIGNEE_SIZE));
  }

  @Override
  public Dimension getPreferredSize() {
    int size = AUTHOR_SIZE + AUTHOR_BORDER * 2;
    return new Dimension(size, size);
  }

  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D)g;
    g2.setBackground(getBackground());

    super.paintComponent(g);

    int authorOffset = AUTHOR_BORDER;
    int assigneeOffset = AUTHOR_SIZE + 2 * AUTHOR_BORDER - ASSIGNEE_BORDER - ASSIGNEE_SIZE;

    if (myAssigneeIcons.isEmpty()) {
      myAuthorIcon.paintIcon(this, g2, authorOffset, authorOffset);
    }
    else {
      Shape oldClip = g2.getClip();

      Area outside = new Area(oldClip);
      outside.subtract(new Area(new Rectangle2D.Float(assigneeOffset - ASSIGNEE_BORDER, assigneeOffset - ASSIGNEE_BORDER,
                                                      ASSIGNEE_SIZE + 2 * ASSIGNEE_BORDER, ASSIGNEE_SIZE + 2 * ASSIGNEE_BORDER)));
      g2.setClip(outside);

      myAuthorIcon.paintIcon(this, g2, authorOffset, authorOffset);

      g2.setClip(oldClip);

      // TODO: paint all assignees / paint "stack"
      myAssigneeIcons.get(0).paintIcon(this, g2, assigneeOffset, assigneeOffset);
    }
  }
}

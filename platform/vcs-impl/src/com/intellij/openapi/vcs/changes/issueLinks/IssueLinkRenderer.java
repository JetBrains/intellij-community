/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class IssueLinkRenderer {
  private final SimpleColoredComponent myColoredComponent;
  private final IssueNavigationConfiguration myIssueNavigationConfiguration;

  public IssueLinkRenderer(final Project project, final SimpleColoredComponent coloredComponent) {
    myColoredComponent = coloredComponent;
    myIssueNavigationConfiguration = IssueNavigationConfiguration.getInstance(project);
  }

  public List<String> appendTextWithLinks(@Nls String text) {
    return appendTextWithLinks(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public List<String> appendTextWithLinks(@Nls String text, @NotNull SimpleTextAttributes baseStyle) {
    List<String> pieces = new ArrayList<>();

    SimpleTextAttributes linkAttributes = getLinkAttributes(baseStyle);
    IssueNavigationConfiguration.processTextWithLinks(text, myIssueNavigationConfiguration.findIssueLinks(text),
                                                      s -> {
                                                        pieces.add(s);
                                                        append(s, baseStyle);
                                                      },
                                                      (link, target) -> {
                                                        pieces.add(link);
                                                        append(link, linkAttributes, target);
                                                      });

    return pieces;
  }

  private void append(@Nls String piece, final SimpleTextAttributes baseStyle) {
    myColoredComponent.append(piece, baseStyle);
  }

  private void append(@Nls String piece, final SimpleTextAttributes baseStyle, @NlsSafe String targetUrl) {
    myColoredComponent.append(piece, baseStyle, new SimpleColoredComponent.BrowserLauncherTag(targetUrl));
  }

  private static SimpleTextAttributes getLinkAttributes(@NotNull SimpleTextAttributes baseStyle) {
    Color color = baseStyle.getFgColor();
    int alpha = color != null ? color.getAlpha() : 255;
    Color linkColor = JBUI.CurrentTheme.Link.linkColor();
    @SuppressWarnings("UseJBColor") Color resultColor = new Color(linkColor.getRed(), linkColor.getGreen(), linkColor.getBlue(), alpha);
    return new SimpleTextAttributes(baseStyle.getStyle() | SimpleTextAttributes.STYLE_UNDERLINE, resultColor);
  }
}

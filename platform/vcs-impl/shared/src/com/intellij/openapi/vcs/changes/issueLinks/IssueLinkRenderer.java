// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


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

  @ApiStatus.Internal
  public static SimpleTextAttributes getLinkAttributes(@NotNull SimpleTextAttributes baseStyle) {
    Color color = baseStyle.getFgColor();
    int alpha = color != null ? color.getAlpha() : 255;
    Color linkColor = JBUI.CurrentTheme.Link.Foreground.ENABLED;
    @SuppressWarnings("UseJBColor") Color resultColor = new Color(linkColor.getRed(), linkColor.getGreen(), linkColor.getBlue(), alpha);
    return new SimpleTextAttributes(baseStyle.getStyle() | SimpleTextAttributes.STYLE_UNDERLINE, resultColor);
  }
}

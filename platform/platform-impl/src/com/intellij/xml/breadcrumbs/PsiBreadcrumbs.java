// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.breadcrumbs.Breadcrumbs;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

final class PsiBreadcrumbs extends Breadcrumbs {
  private final static Logger LOG = Logger.getInstance(PsiBreadcrumbs.class);
  private final Map<Crumb, Promise<String>> scheduledTooltipTasks = new HashMap<>();
  boolean above = EditorSettingsExternalizable.getInstance().isBreadcrumbsAbove();

  void updateBorder(int offset) {
    // do not use scaling here because this border is used to align breadcrumbs with a gutter
    setBorder(new EmptyBorder(0, offset, 0, 0));
  }

  @Override
  protected void paintMarker(Graphics2D g, int x, int y, int width, int height, Crumb crumb, int thickness) {
    super.paintMarker(g, x, y, width, above ? height : thickness, crumb, thickness);
  }

  @Override
  public Color getForeground() {
    if (!isForegroundSet()) {
      Color foreground = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.LINE_NUMBERS_COLOR);
      if (foreground != null) return foreground;
    }
    return super.getForeground();
  }

  @Override
  public Color getBackground() {
    if (!isBackgroundSet()) {
      Color background = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.GUTTER_BACKGROUND);
      if (background != null) return background;
    }
    return super.getBackground();
  }

  @Nullable
  @Override
  public String getToolTipText(MouseEvent event) {
    if (hovered == null) {
      return null;
    }

    if (!(hovered instanceof LazyTooltipCrumb) || !((LazyTooltipCrumb)hovered).needCalculateTooltip()) {
      return hovered.getTooltip();
    }

    final Crumb crumb = hovered;
    Promise<@NlsContexts.Tooltip String> tooltipLazy;
    synchronized (scheduledTooltipTasks) {
      tooltipLazy = scheduledTooltipTasks.get(crumb);
      if (tooltipLazy == null) {
        Runnable removeFinishedTask = () -> {
          synchronized (scheduledTooltipTasks) {
            scheduledTooltipTasks.remove(crumb);
          }
        };
        final IdeTooltipManager tooltipManager = IdeTooltipManager.getInstance();
        final Component component = event == null ? null : event.getComponent();
        tooltipLazy = ReadAction.nonBlocking(() -> crumb.getTooltip())
          .expireWhen(() -> !tooltipManager.isProcessing(component))
          .finishOnUiThread(ModalityState.any(), toolTipText -> tooltipManager.updateShownTooltip(component))
          .submit(AppExecutorUtil.getAppExecutorService())
          .onError(throwable -> {
            if (!(throwable instanceof CancellationException)) {
              LOG.error("Exception in LazyTooltipCrumb", throwable);
            }
            removeFinishedTask.run();
          })
          .onSuccess(toolTipText -> removeFinishedTask.run());
        scheduledTooltipTasks.put(crumb, tooltipLazy);
      }
    }
    if (tooltipLazy.isSucceeded()) {
      try {
        return tooltipLazy.blockingGet(0);
      }
      catch (TimeoutException | ExecutionException e) {
        LOG.error(e);
      }
    }
    return UIBundle.message("crumbs.calculating.tooltip");
  }

  @Override
  protected Color getForeground(Crumb crumb) {
    CrumbPresentation presentation = PsiCrumb.getPresentation(crumb);
    if (presentation == null) return super.getForeground(crumb);

    Color background = super.getBackground(crumb);
    if (background != null) return super.getForeground(crumb);

    return presentation.getBackgroundColor(isSelected(crumb), isHovered(crumb), isAfterSelected(crumb));
  }

  @Override
  protected Color getBackground(Crumb crumb) {
    CrumbPresentation presentation = PsiCrumb.getPresentation(crumb);
    if (presentation == null) return super.getBackground(crumb);

    Color background = super.getBackground(crumb);
    if (background == null) return null;

    return presentation.getBackgroundColor(isSelected(crumb), isHovered(crumb), isAfterSelected(crumb));
  }

  @Override
  protected TextAttributes getAttributes(Crumb crumb) {
    TextAttributesKey key = getKey(crumb);
    return key == null ? null : EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
  }

  private TextAttributesKey getKey(Crumb crumb) {
    if (isHovered(crumb)) return EditorColors.BREADCRUMBS_HOVERED;
    if (isSelected(crumb)) return EditorColors.BREADCRUMBS_CURRENT;
    if (isAfterSelected(crumb)) return EditorColors.BREADCRUMBS_INACTIVE;
    return EditorColors.BREADCRUMBS_DEFAULT;
  }
}

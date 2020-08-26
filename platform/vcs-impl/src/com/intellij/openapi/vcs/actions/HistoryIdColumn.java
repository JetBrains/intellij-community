// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
class HistoryIdColumn extends AnnotationFieldGutter {
  private final Map<VcsRevisionNumber, Integer> myHistoryIds;

  HistoryIdColumn(FileAnnotation annotation,
                  final TextAnnotationPresentation presentation,
                  Couple<Map<VcsRevisionNumber, Color>> colorScheme,
                  Map<VcsRevisionNumber, Integer> ids) {
    super(annotation, presentation, colorScheme);
    myHistoryIds = ids;
  }

  @Override
  public String getLineText(int line, Editor editor) {
    if (!isAvailable()) return "";
    final VcsRevisionNumber revisionNumber = myAnnotation.getLineRevisionNumber(line);
    if (revisionNumber != null) {
      final Integer num = myHistoryIds.get(revisionNumber);
      if (num != null) {
        final String size = String.valueOf(myHistoryIds.size());
        String value = num.toString();
        while (value.length() < size.length()) {
          value = " " + value;
        }
        return value;
      }
    }
    return "";
  }

  @Override
  public boolean isShowByDefault() {
    return false;
  }

  @Override
  public String getID() {
    return "Commit number";
  }

  @Override
  public @NlsContexts.ListItem @Nullable String getDisplayName() {
    return VcsBundle.message("annotation.commit.number");
  }
}

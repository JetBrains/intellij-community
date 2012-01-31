package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.detector.api.Issue;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidLintUtil {
  @NonNls static final String ATTR_VALUE_VERTICAL = "vertical";
  @NonNls static final String ATTR_VALUE_WRAP_CONTENT = "wrap_content";
  @NonNls static final String ATTR_LAYOUT_HEIGHT = "layout_height";
  @NonNls static final String ATTR_LAYOUT_WIDTH = "layout_width";
  @NonNls static final String ATTR_ORIENTATION = "orientation";

  private AndroidLintUtil() {
  }

  @Nullable
  static Pair<AndroidLintInspectionBase, HighlightDisplayLevel> getHighlighLevelAndInspection(@NotNull Issue issue,
                                                                                              @NotNull PsiElement context) {
    final String inspectionShortName = AndroidLintInspectionBase.getInspectionShortNameByIssue(issue);
    if (inspectionShortName == null) {
      return null;
    }

    final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionShortName);
    if (key == null) {
      return null;
    }

    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(context.getProject()).getInspectionProfile();
    if (!profile.isToolEnabled(key, context)) {
      return null;
    }

    final InspectionToolWrapper toolWrapper =
      (InspectionToolWrapper)profile.getInspectionTool(inspectionShortName, context);
    if (toolWrapper == null) {
      return null;
    }

    final AndroidLintInspectionBase inspection = (AndroidLintInspectionBase)toolWrapper.getTool();
    final HighlightDisplayLevel errorLevel = profile.getErrorLevel(key, context);
    return new Pair<AndroidLintInspectionBase, HighlightDisplayLevel>(inspection,
                                                                      errorLevel != null ? errorLevel : HighlightDisplayLevel.WARNING);
  }
}

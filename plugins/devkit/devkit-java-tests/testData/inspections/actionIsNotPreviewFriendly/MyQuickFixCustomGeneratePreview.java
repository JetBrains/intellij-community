import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class MyQuickFix implements LocalQuickFix {
  List<?> list;

  public IntentionPreviewInfo generatePreview(Project project, ProblemDescriptor previewDescriptor) {
    return null;
  }
}
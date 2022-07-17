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
class MyQuickFixWriteFalse implements LocalQuickFix {
  List<?> list;

  public boolean startInWriteAction() {
    return false;
  }
}
class MyQuickFixWriteTrue implements LocalQuickFix {
  List<?> <warning descr="Field may prevent intention preview from working properly">list</warning>;

  public boolean startInWriteAction() {
    return true;
  }
}
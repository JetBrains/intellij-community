package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
abstract class AndroidLintInspectionBase extends GlobalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.inspections.lint.AndroidLintInspectionBase");

  private static final Map<Issue, String> ourIssue2InspectionShortName = new HashMap<Issue, String>();

  private final Issue myIssue;
  private final String[] myGroupPath;
  private final String myDisplayName;

  protected AndroidLintInspectionBase(@NotNull String displayName, @NotNull Issue issue) {
    myIssue = issue;

    final Category category = issue.getCategory();
    final String[] categoryNames = category != null
                                   ? computeAllNames(category)
                                   : ArrayUtil.EMPTY_STRING_ARRAY;

    myGroupPath = ArrayUtil.mergeArrays(new String[]{AndroidBundle.message("android.inspections.group.name"),
      AndroidBundle.message("android.lint.inspections.subgroup.name")}, categoryNames);
    myDisplayName = displayName;

    addIssue(issue, getShortName());
  }

  @Override
  public void runInspection(AnalysisScope scope,
                            final InspectionManager manager,
                            final GlobalInspectionContext globalContext,
                            final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    final AndroidLintGlobalInspectionContext androidLintContext = globalContext.getExtension(AndroidLintGlobalInspectionContext.ID);
    if (androidLintContext == null) {
      return;
    }

    final Map<Issue, Map<File, List<ProblemData>>> problemMap = androidLintContext.getResults();
    if (problemMap == null) {
      return;
    }

    final Map<File, List<ProblemData>> file2ProblemList = problemMap.get(myIssue);
    if (file2ProblemList == null) {
      return;
    }

    for (final Map.Entry<File, List<ProblemData>> entry : file2ProblemList.entrySet()) {
      final File file = entry.getKey();
      final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
      
      if (vFile == null) {
        continue;
      }
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          final PsiManager psiManager = PsiManager.getInstance(globalContext.getProject());
          final PsiFile psiFile = psiManager.findFile(vFile);

          if (psiFile != null) {
            final ProblemDescriptor[] descriptors = computeProblemDescriptors(psiFile, manager, entry.getValue());

            if (descriptors.length > 0) {
              problemDescriptionsProcessor.addProblemElement(globalContext.getRefManager().getReference(psiFile), descriptors);
            }
          }
        }
      });
    }
  }

  @NotNull
  private static ProblemDescriptor[] computeProblemDescriptors(@NotNull PsiFile psiFile,
                                                               @NotNull InspectionManager manager,
                                                               @NotNull List<ProblemData> problems) {
    final List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();

    for (ProblemData problemData : problems) {
      final String message = problemData.getMessage();
      final TextRange range = problemData.getTextRange();

      if (range.getStartOffset() == range.getEndOffset()) {
        PsiFile f = psiFile;

        if (f instanceof PsiBinaryFile) {
          // todo: show inspection in binary file (fix NPE)!
          final Module module = ModuleUtil.findModuleForPsiElement(f);

          if (module != null) {
            final VirtualFile manifestFile = AndroidRootUtil.getManifestFile(module);

            if (manifestFile != null) {
              f = PsiManager.getInstance(f.getProject()).findFile(manifestFile);
            }
          }
        }
        
        if (f != null) {
          result.add(manager.createProblemDescriptor(f, message, false, LocalQuickFix.EMPTY_ARRAY,
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
      else {
        final PsiElement startElement = psiFile.findElementAt(range.getStartOffset());
        final PsiElement endElement = psiFile.findElementAt(range.getEndOffset() - 1);

        if (startElement != null && endElement != null) {
          result.add(manager.createProblemDescriptor(startElement, endElement, message + "#loc",
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false));
        }
      }
    }
    return result.toArray(new ProblemDescriptor[result.size()]);
  }

  private synchronized static void addIssue(@NotNull Issue issue, @NotNull String shortName) {
    ourIssue2InspectionShortName.put(issue, shortName);
  }

  public synchronized static String getInspectionShortNameByIssue(@NotNull Issue issue) {
    return ourIssue2InspectionShortName.get(issue);
  }

  @NotNull
  private static String[] computeAllNames(@NotNull Category category) {
    final List<String> result = new ArrayList<String>();

    Category c = category;

    while (c != null) {
      final String name = c.getName();

      if (name == null) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
      result.add(name);
      c = c.getParent();
    }
    return ArrayUtil.reverseArray(ArrayUtil.toStringArray(result));
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return AndroidBundle.message("android.lint.inspections.group.name");
  }

  @NotNull
  @Override
  public String[] getGroupPath() {
    return myGroupPath;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @SuppressWarnings("deprecation")
  @Override
  public String getStaticDescription() {
    String description = myIssue.getDescription();
    String explanation = myIssue.getExplanation();

    description = description != null ? StringUtil.escapeXml(description) : null;
    explanation = explanation != null ? StringUtil.escapeXml(explanation) : null;

    if (description == null && explanation == null) {
      return "";
    }
    else if (description != null && explanation != null) {
      return "<html><body>" + description + "<br><br>" + explanation + "</body></html>";
    }
    else if (description != null) {
      return "<html><body>" + description + "</body></html>";
    }
    else {
      return "<html><body>" + explanation + "</body></html>";
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return StringUtil.trimEnd(getClass().getSimpleName(), "Inspection");
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    final Severity defaultSeverity = myIssue.getDefaultSeverity();
    if (defaultSeverity == null) {
      return HighlightDisplayLevel.WARNING;
    }
    final HighlightDisplayLevel displayLevel = toHighlightDisplayLevel(defaultSeverity);
    return displayLevel != null ? displayLevel : HighlightDisplayLevel.WARNING;
  }

  @Nullable
  private static HighlightDisplayLevel toHighlightDisplayLevel(@NotNull Severity severity) {
    switch (severity) {
      case ERROR:
        return HighlightDisplayLevel.ERROR;
      case WARNING:
        return HighlightDisplayLevel.WARNING;
      case INFORMATIONAL:
        return HighlightDisplayLevel.WEAK_WARNING;
      case IGNORE:
        return null;
      default:
        LOG.error("Unknown severity " + severity);
        return null;
    }
  }
}

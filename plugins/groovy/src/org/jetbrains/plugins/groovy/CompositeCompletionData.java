package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.completion.CompletionData;
import com.intellij.codeInsight.completion.CompletionVariant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.HashSet;
import java.util.Set;

public class CompositeCompletionData extends CompletionData {
  private static boolean ourShouldCompleteReferences = true;
  private static boolean ourShouldCompleteKeywords = true;

  private final CompletionData[] myDataByPriority;

  public CompositeCompletionData(CompletionData... dataByPriority) {
    myDataByPriority = dataByPriority;
  }

  public void addKeywordVariants(final Set<CompletionVariant> set, final PsiElement position, final PsiFile file) {
    if (!ourShouldCompleteKeywords) return;

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        Set<CompletionVariant> toAdd = new HashSet<CompletionVariant>();
        outer:
        for (final CompletionData completionData : myDataByPriority) {
          completionData.addKeywordVariants(toAdd, position, file);
          for (CompletionVariant completionVariant : toAdd) {
            if (completionVariant.hasKeywordCompletions()) {
              break outer;
            }
          }
        }
        set.addAll(toAdd);
      }
    });
  }

  @TestOnly
  public static void restrictCompletion(final boolean shouldCompleteReferences, final boolean shouldCompleteKeywords) {
    ourShouldCompleteReferences = shouldCompleteReferences;
    ourShouldCompleteKeywords = shouldCompleteKeywords;
  }

  public void completeReference(PsiReference reference, Set<LookupElement> set, @NotNull PsiElement position, final PsiFile file, final int offset) {
    if (!ourShouldCompleteReferences) return;

    myDataByPriority[0].completeReference(reference, set, position, file, offset);
  }

  public String findPrefix(PsiElement insertedElement, int offset) {
    for (final CompletionData completionData : myDataByPriority) {
      final String prefix = completionData.findPrefix(insertedElement, offset);
      if (prefix.length() > 0) return prefix;
    }
    return "";
  }
}

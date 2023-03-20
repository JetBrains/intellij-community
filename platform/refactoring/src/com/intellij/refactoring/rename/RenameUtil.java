// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.rename;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.find.findUsages.FindUsagesHelper;
import com.intellij.ide.actions.FqnUtil;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.UndoRefactoringElementListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.NonCodeSearchDescriptionLocation;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.TextOccurrencesUtilBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.MultiMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class RenameUtil {
  private static final Logger LOG = Logger.getInstance(RenameUtil.class);

  private RenameUtil() {
  }

  public static UsageInfo @NotNull [] findUsages(@NotNull PsiElement element,
                                                 String newName,
                                                 boolean searchInStringsAndComments,
                                                 boolean searchForTextOccurrences,
                                                 Map<? extends PsiElement, String> allRenames) {
    return findUsages(element, newName, GlobalSearchScope.projectScope(element.getProject()),
                      searchInStringsAndComments, searchForTextOccurrences, allRenames);
  }

  public static UsageInfo @NotNull [] findUsages(@NotNull PsiElement element,
                                                 String newName,
                                                 @NotNull SearchScope searchScope,
                                                 boolean searchInStringsAndComments,
                                                 boolean searchForTextOccurrences,
                                                 Map<? extends PsiElement, String> allRenames) {
    List<UsageInfo> result = Collections.synchronizedList(new ArrayList<>());

    RenamePsiElementProcessorBase elementProcessor = RenamePsiElementProcessorBase.forPsiElement(element);

    processUsages(element, elementProcessor, newName, searchScope, true, searchInStringsAndComments, searchForTextOccurrences, info -> {
                    result.add(info);
                    return true;
                  }
    );

    elementProcessor.findCollisions(element, newName, allRenames, result);

    return result.toArray(UsageInfo.EMPTY_ARRAY);
  }

  public static boolean hasNonCodeUsages(@NotNull PsiElement element,
                                         String newName,
                                         @NotNull SearchScope searchScope,
                                         boolean searchInStringsAndComments,
                                         boolean searchForTextOccurrences) {
    RenamePsiElementProcessorBase elementProcessor = RenamePsiElementProcessorBase.forPsiElement(element);
    return !processUsages(element, elementProcessor, newName, searchScope, false, searchInStringsAndComments, searchForTextOccurrences, info -> false
    );
  }

  private static boolean processUsages(
    @NotNull PsiElement element,
    @NotNull RenamePsiElementProcessorBase elementProcessor,
    String newName,
    @NotNull SearchScope searchScope,
    boolean searchInCode,
    boolean searchInStringsAndComments,
    boolean searchForTextOccurrences,
    @NotNull Processor<? super UsageInfo> processor
  ) {
    SearchScope useScope = PsiSearchHelper.getInstance(element.getProject()).getUseScope(element);
    if (!(useScope instanceof LocalSearchScope)) {
      useScope = searchScope.intersectWith(useScope);
    }

    if (searchInCode) {
      Collection<PsiReference> refs = elementProcessor.findReferences(element, useScope, searchInStringsAndComments);
      for (final PsiReference ref : refs) {
        if (ref == null) {
          LOG.error("null reference from processor " + elementProcessor);
          continue;
        }
        PsiElement referenceElement = ref.getElement();
        if (!processor.process(elementProcessor.createUsageInfo(element, ref, referenceElement))) return false;
      }
    }

    final PsiElement searchForInComments = elementProcessor.getElementToSearchInStringsAndComments(element);

    if (searchInStringsAndComments && searchForInComments != null) {
      String stringToSearch = ElementDescriptionUtil.getElementDescription(searchForInComments, NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
      if (stringToSearch.length() > 0) {
        final String stringToReplace = getStringToReplace(element, newName, false, elementProcessor);
        UsageInfoFactory factory = new NonCodeUsageInfoFactory(searchForInComments, stringToReplace);
        if (!TextOccurrencesUtilBase.processUsagesInStringsAndComments(processor, searchForInComments,
                                                                       searchScope, stringToSearch, factory)) return false;
      }
    }

    if (searchForTextOccurrences && searchForInComments != null) {
      String stringToSearch = ElementDescriptionUtil.getElementDescription(searchForInComments, NonCodeSearchDescriptionLocation.NON_JAVA);
      if (stringToSearch.length() > 0) {
        final String stringToReplace = getStringToReplace(element, newName, true, elementProcessor);
        if (!processTextOccurrences(searchForInComments, searchScope, stringToSearch, stringToReplace, processor)) return false;
      }

      final Pair<String, String> additionalStringToSearch = elementProcessor.getTextOccurrenceSearchStrings(searchForInComments, newName);
      if (additionalStringToSearch != null && additionalStringToSearch.first.length() > 0) {
        if (!processTextOccurrences(searchForInComments, searchScope, additionalStringToSearch.first, additionalStringToSearch.second,
                                    processor
        )) return false;
      }
    }

    return true;
  }

  private static boolean processTextOccurrences(
    @NotNull PsiElement element,
    @NotNull SearchScope searchScope,
    @NotNull String stringToSearch,
    String stringToReplace,
    @NotNull Processor<? super UsageInfo> processor
  ) {
    UsageInfoFactory factory = new UsageInfoFactory() {
      @Override
      public UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset) {
        TextRange textRange = usage.getTextRange();
        int start = textRange == null ? 0 : textRange.getStartOffset();
        return NonCodeUsageInfo.create(usage.getContainingFile(), start + startOffset, start + endOffset, element, stringToReplace);
      }
    };
    if (searchScope instanceof GlobalSearchScope) {
      return FindUsagesHelper.processTextOccurrences(element, stringToSearch, (GlobalSearchScope)searchScope, factory, processor);
    }
    else {
      return true;
    }
  }


  public static void buildPackagePrefixChangedMessage(final VirtualFile[] virtualFiles, StringBuffer message, final String qualifiedName) {
    if (virtualFiles.length > 0) {
      message.append(RefactoringBundle.message("package.occurs.in.package.prefixes.of.the.following.source.folders.n", qualifiedName));
      for (final VirtualFile virtualFile : virtualFiles) {
        message.append(virtualFile.getPresentableUrl()).append("\n");
      }
      message.append(RefactoringBundle.message("these.package.prefixes.will.be.changed"));
    }
  }

  private static String getStringToReplace(PsiElement element, String newName, boolean nonJava, final RenamePsiElementProcessorBase theProcessor) {
    if (element instanceof PsiMetaOwner psiMetaOwner) {
      final PsiMetaData metaData = psiMetaOwner.getMetaData();
      if (metaData != null) {
        return metaData.getName();
      }
    }

    if (theProcessor != null) {
      String result = theProcessor.getQualifiedNameAfterRename(element, newName, nonJava);
      if (result != null) {
        return result;
      }
    }

    if (element instanceof PsiNamedElement) {
      return newName;
    }
    else {
      LOG.error("Unknown element type : " + element);
      return null;
    }
  }

  public static void checkRename(PsiElement element, String newName) throws IncorrectOperationException {
    if (element instanceof PsiCheckedRenameElement) {
      ((PsiCheckedRenameElement)element).checkSetName(newName);
    }
  }

  static void registerUndoableRename(PsiElement element, @Nullable RefactoringElementListener listener) {
    final String fqn = element instanceof PsiFile ? ((PsiFile)element).getVirtualFile().getPath() : FqnUtil.elementToFqn(element, null);
    if (fqn != null) {
      UndoableAction action = new BasicUndoableAction() {
        @Override
        public void undo() {
          if (listener instanceof UndoRefactoringElementListener) {
            ((UndoRefactoringElementListener)listener).undoElementMovedOrRenamed(element, fqn);
          }
        }

        @Override
        public void redo() {
        }
      };
      UndoManager.getInstance(element.getProject()).undoableActionPerformed(action);
    }
  }

  public static void doRename(final PsiElement element, String newName, UsageInfo[] usages, final Project project,
                              @Nullable final RefactoringElementListener listener) throws IncorrectOperationException{
    registerUndoableRename(element, listener);
    RenamePsiElementProcessorBase processor = RenamePsiElementProcessorBase.forPsiElement(element);
    processor.renameElement(element, newName, usages, listener);
  }

  public static void showErrorMessage(final IncorrectOperationException e, final PsiElement element, final Project project) {
    // may happen if the file or package cannot be renamed. e.g. locked by another application
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(e);
      //LOG.error(e);
      //return;
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      final String helpID = RenamePsiElementProcessorBase.forPsiElement(element).getHelpID(element);
      String message = e.getMessage();
      if (StringUtil.isEmpty(message)) {
        message = RefactoringBundle.message("rename.not.supported");
      }
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("rename.title"), message, helpID, project);
    });
  }

  public static void doRenameGenericNamedElement(@NotNull PsiElement namedElement, String newName, UsageInfo[] usages,
                                                 @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    Set<Class> reportedClasses = new HashSet<>();
    for (UsageInfo usage : usages) {
      PsiReference reference = usage.getReference();
      if (reference != null && reportedClasses.add(reference.getClass())) {
        RenameUsagesCollector.referenceProcessed.log(namedElement.getProject(), reference.getClass());
      }
    }
    RenameUtilBase.doRenameGenericNamedElement(namedElement, newName, usages, listener);
  }

  public static void rename(UsageInfo info, String newName) throws IncorrectOperationException {
    RenameUtilBase.rename(info, newName);
  }

  @Nullable
  public static List<UnresolvableCollisionUsageInfo> removeConflictUsages(Set<UsageInfo> usages) {
    final List<UnresolvableCollisionUsageInfo> result = new ArrayList<>();
    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      if (usageInfo instanceof UnresolvableCollisionUsageInfo) {
        result.add((UnresolvableCollisionUsageInfo)usageInfo);
        iterator.remove();
      }
    }
    return result.isEmpty() ? null : result;
  }

  public static void addConflictDescriptions(UsageInfo[] usages, MultiMap<PsiElement, String> conflicts) {
    for (UsageInfo usage : usages) {
      if (usage instanceof UnresolvableCollisionUsageInfo) {
        conflicts.putValue(usage.getElement(), ((UnresolvableCollisionUsageInfo)usage).getDescription());
      }
    }
  }

  public static void renameNonCodeUsages(@NotNull Project project, NonCodeUsageInfo @NotNull [] usages) {
    Map<Document, Int2ObjectMap<UsageOffset>> docsToOffsetsMap = CollectionFactory.createSmallMemoryFootprintMap();
    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    for (NonCodeUsageInfo usage : usages) {
      PsiElement element = usage.getElement();

      if (element == null) continue;
      element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element, true);
      if (element == null) continue;

      final ProperTextRange rangeInElement = usage.getRangeInElement();
      if (rangeInElement == null) continue;

      final PsiFile containingFile = element.getContainingFile();
      Document document = containingFile.getViewProvider().getDocument();
      if (document != null) {
        psiDocumentManager.commitDocument(document);
      }

      final Segment segment = usage.getSegment();
      LOG.assertTrue(segment != null);
      TextRange replaceRange = TextRange.create(segment);

      // re-map usages to upper host from injected document to avoid duplicated replacements
      while (document instanceof DocumentWindow documentWindow) {
        replaceRange = documentWindow.injectedToHost(replaceRange);
        document = documentWindow.getDelegate();
      }
      int fileOffset = replaceRange.getStartOffset();

      Int2ObjectMap<UsageOffset> offsetMap = docsToOffsetsMap.get(document);
      if (offsetMap == null) {
        offsetMap = new Int2ObjectOpenHashMap<>();
        docsToOffsetsMap.put(document, offsetMap);
      }
      final UsageOffset substitution = new UsageOffset(fileOffset, fileOffset + rangeInElement.getLength(), usage.newText);
      final UsageOffset duplicate = offsetMap.get(fileOffset);
      if (duplicate != null) {
        if (!duplicate.equals(substitution)) {
          LOG.warn("ATTENTION! Unequal renaming in the same place in the document, possibly due to injection (read more in CPP-17316):\n"
                    + "      document: " + document + "\n"
                    + "       element: " + element + "\n"
                    + "  first rename: " + substitution.newText + "(" + substitution.startOffset + ", " + substitution.endOffset + ")\n"
                    + " second rename: " + duplicate.newText + "(" + duplicate.startOffset + ", " + duplicate.endOffset + ")");
        }
      }
      else {
        offsetMap.put(fileOffset, substitution);
      }
    }

    for (Document document : docsToOffsetsMap.keySet()) {
      Map<Integer, UsageOffset> offsetMap = docsToOffsetsMap.get(document);
      LOG.assertTrue(offsetMap != null, document);

      UsageOffset[] offsets = offsetMap.values().toArray(new UsageOffset[0]);
      Arrays.sort(offsets);

      for (int i = offsets.length - 1; i >= 0; i--) {
        UsageOffset usageOffset = offsets[i];
        document.replaceString(usageOffset.startOffset, usageOffset.endOffset, usageOffset.newText);
      }
      psiDocumentManager.commitDocument(document);
    }
  }

  public static boolean isValidName(final Project project, final PsiElement psiElement, final String newName) {
    if (newName == null || newName.length() == 0) {
      return false;
    }
    final Condition<String> inputValidator = RenameInputValidatorRegistry.getInputValidator(psiElement);
    if (inputValidator != null) {
      return inputValidator.value(newName);
    }
    if (psiElement instanceof PsiFile || psiElement instanceof PsiDirectory) {
      return newName.indexOf('\\') < 0 && newName.indexOf('/') < 0;
    }
    if (psiElement instanceof PomTargetPsiElement) {
      return !StringUtil.isEmptyOrSpaces(newName);
    }

    final PsiFile file = psiElement.getContainingFile();
    final Language elementLanguage = psiElement.getLanguage();

    final Language fileLanguage = file == null ? null : file.getLanguage();
    Language language = fileLanguage == null ? elementLanguage : fileLanguage.isKindOf(elementLanguage) ? fileLanguage : elementLanguage;

    return LanguageNamesValidation.isIdentifier(language, newName.trim(), project);
  }

  public static void assertNonCompileElement(PsiElement element) {
    LOG.assertTrue(!(element instanceof PsiCompiledElement), element);
  }

  private record UsageOffset(int startOffset, int endOffset, String newText) implements Comparable<UsageOffset> {
    @Override
    public int compareTo(@NotNull final UsageOffset o) {
      return startOffset - o.startOffset;
    }
  }

  @NotNull
  public static String getUsageViewType(@NotNull PsiElement element) {
    return ElementDescriptionUtil.getElementDescription(element, UsageViewTypeLocation.INSTANCE);
  }
}
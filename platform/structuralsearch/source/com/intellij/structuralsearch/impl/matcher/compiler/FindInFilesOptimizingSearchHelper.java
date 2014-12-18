package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.util.Processor;
import gnu.trove.THashMap;

import java.util.Set;

/**
 * @author Maxim.Mossienko
*/
class FindInFilesOptimizingSearchHelper extends OptimizingSearchHelperBase {
  private final MyFileProcessor myFileProcessor;
  private THashMap<PsiFile,PsiFile> filesToScan;
  private THashMap<PsiFile,PsiFile> filesToScan2;

  private final boolean myFindMatchingFiles;
  private final Project myProject;

  FindInFilesOptimizingSearchHelper(CompileContext context, boolean findMatchingFiles, Project project) {
    super(context);
    myFindMatchingFiles = findMatchingFiles;
    myProject = project;

    if (myFindMatchingFiles && filesToScan == null) {
      filesToScan = new THashMap<PsiFile, PsiFile>();
      filesToScan2 = new THashMap<PsiFile, PsiFile>();
    }
    myFileProcessor = new MyFileProcessor();
  }

  public boolean doOptimizing() {
    return myFindMatchingFiles;
  }

  public void clear() {
    super.clear();

    if (filesToScan != null) {
      filesToScan.clear();
      filesToScan2.clear();
    }
  }

  protected void doAddSearchWordInCode(final String refname) {
    final MatchOptions options = context.getOptions();
    final FileType fileType = options.getFileType();
    final Language language = fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : Language.ANY;
    final NamesValidator namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(language);
    if (namesValidator.isKeyword(refname, context.getProject())) {
      CacheManager.SERVICE.getInstance(myProject).processFilesWithWord(myFileProcessor, refname, UsageSearchContext.IN_PLAIN_TEXT,
                                                                       (GlobalSearchScope)options.getScope(),
                                                                       options.isCaseSensitiveMatch());
    }
    else {
      CacheManager.SERVICE.getInstance(myProject).processFilesWithWord(myFileProcessor, refname, UsageSearchContext.IN_CODE,
                                                                       (GlobalSearchScope)options.getScope(), options.isCaseSensitiveMatch());
    }
  }

  protected void doAddSearchWordInText(final String refname) {
    final MatchOptions options = context.getOptions();
    CacheManager.SERVICE.getInstance(myProject).processFilesWithWord(myFileProcessor, refname, UsageSearchContext.IN_PLAIN_TEXT,
                                                                     (GlobalSearchScope)options.getScope(), options.isCaseSensitiveMatch());
  }

  protected void doAddSearchWordInComments(final String refname) {
    final MatchOptions options = context.getOptions();
    CacheManager.SERVICE.getInstance(myProject).processFilesWithWord(myFileProcessor, refname, UsageSearchContext.IN_COMMENTS,
                                                                     (GlobalSearchScope)options.getScope(), options.isCaseSensitiveMatch());
  }

  protected void doAddSearchWordInLiterals(final String refname) {
    final MatchOptions options = context.getOptions();
    CacheManager.SERVICE.getInstance(myProject).processFilesWithWord(myFileProcessor, refname, UsageSearchContext.IN_STRINGS,
                                                                     (GlobalSearchScope)options.getScope(), options.isCaseSensitiveMatch());
  }

  public void endTransaction() {
    super.endTransaction();
    THashMap<PsiFile,PsiFile> map = filesToScan;
    if (map.size() > 0) map.clear();
    filesToScan = filesToScan2;
    filesToScan2 = map;
  }

  public Set<PsiFile> getFilesSetToScan() {
    return filesToScan.keySet();
  }

  private class MyFileProcessor implements Processor<PsiFile> {
    public boolean process(PsiFile file) {
      if (scanRequest == 0 || filesToScan.get(file) != null) {
        filesToScan2.put(file,file);
      }
      return true;
    }
  }

}

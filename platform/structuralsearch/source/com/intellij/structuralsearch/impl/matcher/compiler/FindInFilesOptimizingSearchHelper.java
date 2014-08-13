package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.Processor;
import gnu.trove.THashMap;

import java.util.Set;

/**
 * @author Maxim.Mossienko
*/
class FindInFilesOptimizingSearchHelper extends OptimizingSearchHelperBase {
  private PsiSearchHelper helper;
  private THashMap<PsiFile,PsiFile> filesToScan;
  private THashMap<PsiFile,PsiFile> filesToScan2;

  private final boolean findMatchingFiles;

  FindInFilesOptimizingSearchHelper(CompileContext _context, boolean _findMatchngFiles, Project project) {
    super(_context);
    findMatchingFiles = _findMatchngFiles;

    if (findMatchingFiles) {
      helper = PsiSearchHelper.SERVICE.getInstance(project);

      if (filesToScan == null) {
        filesToScan = new THashMap<PsiFile,PsiFile>();
        filesToScan2 = new THashMap<PsiFile,PsiFile>();
      }
    }
  }

  public boolean doOptimizing() {
    return findMatchingFiles;
  }

  public void clear() {
    super.clear();

    if (filesToScan != null) {
      filesToScan.clear();
      filesToScan2.clear();

      helper = null;
    }
  }

  protected void doAddSearchWordInCode(final String refname) {
    final FileType fileType = context.getOptions().getFileType();
    final Language language = fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : Language.ANY;
    final NamesValidator namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(language);
    if (namesValidator.isKeyword(refname, context.getProject())) {
      helper.processAllFilesWithWordInText(refname, (GlobalSearchScope)context.getOptions().getScope(), new MyFileProcessor(), true);
    } else {
      helper.processAllFilesWithWord(refname, (GlobalSearchScope)context.getOptions().getScope(), new MyFileProcessor(), true);
    }
  }

  protected void doAddSearchWordInText(final String refname) {
    helper.processAllFilesWithWordInText(refname, (GlobalSearchScope)context.getOptions().getScope(), new MyFileProcessor(), true);
  }

  protected void doAddSearchWordInComments(final String refname) {
    helper.processAllFilesWithWordInComments(refname, (GlobalSearchScope)context.getOptions().getScope(), new MyFileProcessor());
  }

  protected void doAddSearchWordInLiterals(final String refname) {
    helper.processAllFilesWithWordInLiterals(refname, (GlobalSearchScope)context.getOptions().getScope(), new MyFileProcessor());
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
      if (scanRequest == 0 ||
          filesToScan.get(file)!=null) {
        filesToScan2.put(file,file);
      }
      return true;
    }
  }

}

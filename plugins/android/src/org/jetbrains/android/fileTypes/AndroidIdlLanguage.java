/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.fileTypes;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import org.jetbrains.annotations.NonNls;

/**
 * Android IDL Language.
 *
 * @author Alexey Efimov
 */
public class AndroidIdlLanguage extends Language {
  

  @NonNls
  private static final String ID = "AIDL";

  private final JavaLanguage myJavaLanguage;

  public AndroidIdlLanguage() {
    super(ID);
    myJavaLanguage = Language.findInstance(JavaLanguage.class);
  }

/*  TODO[yole]
    @Override
    @NotNull
    public TokenSet getReadableTextContainerElements() {
        return myJavaLanguage.getReadableTextContainerElements();
    }

    @Override
    @Nullable
    public StructureViewBuilder getStructureViewBuilder(PsiFile psiFile) {
        return myJavaLanguage.getStructureViewBuilder(psiFile);
    }

    @Override
    @NotNull
    public RefactoringSupportProvider getRefactoringSupportProvider() {
        return myJavaLanguage.getRefactoringSupportProvider();
    }

    @Override
    @NotNull
    public SurroundDescriptor[] getSurroundDescriptors() {
        return myJavaLanguage.getSurroundDescriptors();
    }

    @Override
    @Nullable
    public ImportOptimizer getImportOptimizer() {
        return myJavaLanguage.getImportOptimizer();
    }

    @Override
    @NotNull
    public SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile virtualFile) {
        return myJavaLanguage.getSyntaxHighlighter(project, virtualFile);
    }

    @Override
    @Nullable
    public ParameterInfoHandler[] getParameterInfoHandlers() {
        return myJavaLanguage.getParameterInfoHandlers();
    }

    @Override
    public FoldingBuilder getFoldingBuilder() {
        return myJavaLanguage.getFoldingBuilder();
    }

    @Override
    public PairedBraceMatcher getPairedBraceMatcher() {
        return myJavaLanguage.getPairedBraceMatcher();
    }

    @Override
    public Annotator getAnnotator() {
        return myJavaLanguage.getAnnotator();
    }

    @Override
    public ExternalAnnotator getExternalAnnotator() {
        return myJavaLanguage.getExternalAnnotator();
    }

    @Override
    @NotNull
    public NamesValidator getNamesValidator() {
        return myJavaLanguage.getNamesValidator();
    }

    @Override
    public FileViewProvider createViewProvider(VirtualFile file, PsiManager manager, boolean physical) {
        return myJavaLanguage.createViewProvider(file, manager, physical);
    }

    @Override
    public LanguageDialect[] getAvailableLanguageDialects() {
        return myJavaLanguage.getAvailableLanguageDialects();
    }

    @Override
    public LanguageCodeInsightActionHandler getGotoSuperHandler() {
        return myJavaLanguage.getGotoSuperHandler();
    }

    @Override
    public LanguageCodeInsightActionHandler getImplementMethodsHandler() {
        return myJavaLanguage.getImplementMethodsHandler();
    }

    @Override
    public LanguageCodeInsightActionHandler getOverrideMethodsHandler() {
        return myJavaLanguage.getOverrideMethodsHandler();
    }
*/
}

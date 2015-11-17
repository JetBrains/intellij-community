/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.codeInspection.unused.ImplicitPropertyUsageProvider;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.ide.FileIconProvider;
import com.intellij.javaee.CoreExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.mock.MockProject;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.ClassTypePointerFactory;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.ResolveScopeProvider;
import com.intellij.psi.codeStyle.ReferenceAdjuster;
import com.intellij.psi.impl.ExpressionConverter;
import com.intellij.psi.impl.LanguageConstantExpressionEvaluator;
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.psi.search.searches.*;
import com.intellij.psi.stubs.StubIndexExtension;
import com.intellij.util.QueryExecutor;
import com.intellij.util.indexing.FileBasedIndexExtension;
import org.jetbrains.plugins.groovy.*;
import org.jetbrains.plugins.groovy.annotator.GrAnnotatorImpl;
import org.jetbrains.plugins.groovy.annotator.GrKeywordAndDeclarationHighlightFactory;
import org.jetbrains.plugins.groovy.annotator.GrReferenceHighlighterFactory;
import org.jetbrains.plugins.groovy.annotator.GroovyFrameworkConfigNotification;
import org.jetbrains.plugins.groovy.annotator.checkers.*;
import org.jetbrains.plugins.groovy.codeInsight.GroovyClsCustomNavigationPolicy;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.codeInspection.declaration.GrMethodMayBeStaticInspectionFilter;
import org.jetbrains.plugins.groovy.codeInspection.local.GroovyUnusedImportsPassFactory;
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.configSlurper.ConfigSlurperMapContentProvider;
import org.jetbrains.plugins.groovy.configSlurper.GroovyMapValueTypeEnhancer;
import org.jetbrains.plugins.groovy.dgm.DGMImplicitPropertyUsageProvider;
import org.jetbrains.plugins.groovy.dgm.DGMMemberContributor;
import org.jetbrains.plugins.groovy.dgm.GroovyExtensionProvider;
import org.jetbrains.plugins.groovy.dsl.DslActivationStatus;
import org.jetbrains.plugins.groovy.dsl.GroovyDslAnnotator;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider;
import org.jetbrains.plugins.groovy.dsl.dsltop.GroovyDslDefaultMembers;
import org.jetbrains.plugins.groovy.dsl.psi.*;
import org.jetbrains.plugins.groovy.extensions.*;
import org.jetbrains.plugins.groovy.findUsages.*;
import org.jetbrains.plugins.groovy.geb.*;
import org.jetbrains.plugins.groovy.gpp.GppClosureParameterTypeProvider;
import org.jetbrains.plugins.groovy.gpp.GppExpectedTypesContributor;
import org.jetbrains.plugins.groovy.gpp.GppTypeConverter;
import org.jetbrains.plugins.groovy.lang.folding.GroovyFoldingBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesContributor;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.javaView.GroovyClassFinder;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrPrivateFieldScopeEnlarger;
import org.jetbrains.plugins.groovy.lang.psi.impl.smartPointers.GrClassReferenceTypePointerFactory;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.*;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.*;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.*;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator;
import org.jetbrains.plugins.groovy.lang.resolve.*;
import org.jetbrains.plugins.groovy.lang.resolve.ast.*;
import org.jetbrains.plugins.groovy.lang.resolve.noncode.GrCollectionTypeMembersProvider;
import org.jetbrains.plugins.groovy.lang.resolve.noncode.MixinMemberContributor;
import org.jetbrains.plugins.groovy.lang.stubs.GroovyShortNamesCache;
import org.jetbrains.plugins.groovy.markup.XmlMarkupBuilderNonCodeMemberContributor;
import org.jetbrains.plugins.groovy.spock.SpockMemberContributor;
import org.jetbrains.plugins.groovy.spock.SpockPomDeclarationSearcher;
import org.jetbrains.plugins.groovy.structure.GroovyStructureViewFactory;
import org.jetbrains.plugins.groovy.swingBuilder.SwingBuilderNamedArgumentProvider;
import org.jetbrains.plugins.groovy.swingBuilder.SwingBuilderNonCodeMemberContributor;

/**
 * Upsource
 */
@SuppressWarnings("UnusedDeclaration")
public class GroovyCoreEnvironment {
  public static class ApplicationEnvironment {
    public ApplicationEnvironment(CoreApplicationEnvironment appEnvironment) {
      appEnvironment.registerFileType(GroovyFileType.GROOVY_FILE_TYPE, GroovyFileType.DEFAULT_EXTENSION);

      appEnvironment.addExplicitExtension(SyntaxHighlighterFactory.LANGUAGE_FACTORY, GroovyLanguage.INSTANCE,
                                          new GroovySyntaxHighlighterFactory());

      appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, GroovyLanguage.INSTANCE, new GroovyParserDefinition());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GrMethodComparator.EP_NAME, GrMethodComparator.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), NonCodeMembersContributor.EP_NAME, NonCodeMembersContributor.class);
      appEnvironment.addExtension(NonCodeMembersContributor.EP_NAME, new GebBrowserMemberContributor());
      appEnvironment.addExtension(NonCodeMembersContributor.EP_NAME, new GebJUnitTestMemberContributor());
      appEnvironment.addExtension(NonCodeMembersContributor.EP_NAME, new GebModuleMemberContributor());
      appEnvironment.addExtension(NonCodeMembersContributor.EP_NAME, new GebPageMemberContributor());
      appEnvironment.addExtension(NonCodeMembersContributor.EP_NAME, new GebSpockTestMemberContributor());
      appEnvironment.addExtension(NonCodeMembersContributor.EP_NAME, new GebTestNGTestMemberContributor());
      appEnvironment.addExtension(NonCodeMembersContributor.EP_NAME, new GrCollectionTypeMembersProvider());
      appEnvironment.addExtension(NonCodeMembersContributor.EP_NAME, new MixinMemberContributor());
      appEnvironment.addExtension(NonCodeMembersContributor.EP_NAME, new SpockMemberContributor());
      appEnvironment.addExtension(NonCodeMembersContributor.EP_NAME, new XmlMarkupBuilderNonCodeMemberContributor());
      appEnvironment.addExtension(NonCodeMembersContributor.EP_NAME, new DGMMemberContributor());
      appEnvironment.addExtension(NonCodeMembersContributor.EP_NAME, new SwingBuilderNonCodeMemberContributor());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), DefaultImportContributor.EP_NAME,
                                                        DefaultImportContributor.class);

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), AstTransformContributor.EP_NAME, AstTransformContributor.class);
      appEnvironment.addExtension(AstTransformContributor.EP_NAME, new AutoCloneContributor());
      appEnvironment.addExtension(AstTransformContributor.EP_NAME, new AutoExternalizeContributor());
      appEnvironment.addExtension(AstTransformContributor.EP_NAME, new ConstructorAnnotationsProcessor());
      appEnvironment.addExtension(AstTransformContributor.EP_NAME, new DelegatedMethodsContributor());
      appEnvironment.addExtension(AstTransformContributor.EP_NAME, new GrInheritConstructorContributor());
      appEnvironment.addExtension(AstTransformContributor.EP_NAME, new LoggingContributor());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ClosureMissingMethodContributor.EP_NAME,
                                                        ClosureMissingMethodContributor.class);
      appEnvironment.addExtension(ClosureMissingMethodContributor.EP_NAME, new PluginXmlClosureMemberContributor());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GrVariableEnhancer.EP_NAME, GrVariableEnhancer.class);
      appEnvironment.addExtension(GrVariableEnhancer.EP_NAME, new ClosureAsAnonymousParameterEnhancer());
      appEnvironment.addExtension(GrVariableEnhancer.EP_NAME, new ClosureParameterEnhancer());
      appEnvironment.addExtension(GrVariableEnhancer.EP_NAME, new ClosureParamsEnhancer());
      appEnvironment.addExtension(GrVariableEnhancer.EP_NAME, new GppClosureParameterTypeProvider());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GrReferenceTypeEnhancer.EP_NAME,
                                                        GrReferenceTypeEnhancer.class);
      appEnvironment.addExtension(GrReferenceTypeEnhancer.EP_NAME, new GroovyMapValueTypeEnhancer());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GrTypeConverter.EP_NAME, GrTypeConverter.class);
      appEnvironment.addExtension(GrTypeConverter.EP_NAME, new GrBooleanTypeConverter());
      appEnvironment.addExtension(GrTypeConverter.EP_NAME, new GrGenericTypeConverter());
      appEnvironment.addExtension(GrTypeConverter.EP_NAME, new GrClassConverter());
      appEnvironment.addExtension(GrTypeConverter.EP_NAME, new GrNullVoidConverter());
      appEnvironment.addExtension(GrTypeConverter.EP_NAME, new GrCharConverter());
      appEnvironment.addExtension(GrTypeConverter.EP_NAME, new GrNumberConverter());
      appEnvironment.addExtension(GrTypeConverter.EP_NAME, new GrStringConverter());
      appEnvironment.addExtension(GrTypeConverter.EP_NAME, new GrEnumConverter());
      appEnvironment.addExtension(GrTypeConverter.EP_NAME, new GrContainerTypeConverter());
      appEnvironment.addExtension(GrTypeConverter.EP_NAME, new ClosureToSamConverter());
      appEnvironment.addExtension(GrTypeConverter.EP_NAME, new GrContainerConverter());
      appEnvironment.addExtension(GrTypeConverter.EP_NAME, new GppTypeConverter());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GroovyExpectedTypesContributor.EP_NAME,
                                                        GroovyExpectedTypesContributor.class);
      appEnvironment.addExtension(GroovyExpectedTypesContributor.EP_NAME, new GppExpectedTypesContributor());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GroovyScriptTypeDetector.EP_NAME,
                                                        GroovyScriptTypeDetector.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GroovyNamedArgumentProvider.EP_NAME, GroovyNamedArgumentProvider.class);
      appEnvironment.addExtension(GroovyNamedArgumentProvider.EP_NAME, new GroovyConstructorNamedArgumentProvider());
      appEnvironment.addExtension(GroovyNamedArgumentProvider.EP_NAME, new GroovyMethodReturnNamedArgumentProvider());
      appEnvironment.addExtension(GroovyNamedArgumentProvider.EP_NAME, new GroovySourceCodeNamedArgumentProvider());
      appEnvironment.addExtension(GroovyNamedArgumentProvider.EP_NAME, new SwingBuilderNamedArgumentProvider());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GroovyMapContentProvider.EP_NAME,
                                                        GroovyMapContentProvider.class);
      appEnvironment.addExtension(GroovyMapContentProvider.EP_NAME, new ConfigSlurperMapContentProvider());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GroovyUnresolvedHighlightFilter.EP_NAME,
                                                        GroovyUnresolvedHighlightFilter.class);
      appEnvironment.addExtension(GroovyUnresolvedHighlightFilter.EP_NAME, new GroovyUnresolvedReferenceFilterByFile());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GroovyUnresolvedHighlightFileFilter.EP_NAME,
                                                        GroovyUnresolvedHighlightFileFilter.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GrCallExpressionTypeCalculator.EP_NAME, GrCallExpressionTypeCalculator.class);
      appEnvironment.addExtension(GrCallExpressionTypeCalculator.EP_NAME, new DefaultCallExpressionTypeCalculator());
      appEnvironment.addExtension(GrCallExpressionTypeCalculator.EP_NAME, new GrDescriptorReturnTypeCalculator());
      appEnvironment.addExtension(GrCallExpressionTypeCalculator.EP_NAME, new GrDGMTypeCalculator());
      appEnvironment.addExtension(GrCallExpressionTypeCalculator.EP_NAME, new GrWithTraitTypeCalculator());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GrExpressionTypeCalculator.EP_NAME, GrExpressionTypeCalculator.class);
      appEnvironment.addExtension(GrExpressionTypeCalculator.EP_NAME, new GrClosureDelegateTypeCalculator());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GroovyClassDescriptor.EP_NAME,
                                                        GroovyClassDescriptor.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GroovyMethodDescriptorExtension.EP_NAME,
                                                        GroovyMethodDescriptorExtension.class);

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), PsiEnhancerCategory.EP_NAME, PsiEnhancerCategory.class);
      appEnvironment.addExtension(PsiEnhancerCategory.EP_NAME, new GrExpressionCategory());
      appEnvironment.addExtension(PsiEnhancerCategory.EP_NAME, new PsiClassCategory());
      appEnvironment.addExtension(PsiEnhancerCategory.EP_NAME, new PsiElementCategory());
      appEnvironment.addExtension(PsiEnhancerCategory.EP_NAME, new PsiExpressionCategory());
      appEnvironment.addExtension(PsiEnhancerCategory.EP_NAME, new PsiMethodCategory());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GdslMembersProvider.EP_NAME, GdslMembersProvider.class);
      appEnvironment.addExtension(GdslMembersProvider.EP_NAME, new GdkMethodDslProvider());
      appEnvironment.addExtension(GdslMembersProvider.EP_NAME, new GroovyDslDefaultMembers());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GroovyFrameworkConfigNotification.EP_NAME,
                                                        GroovyFrameworkConfigNotification.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), GrMethodMayBeStaticInspectionFilter.EP_NAME, GrMethodMayBeStaticInspectionFilter.class);
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), CustomAnnotationChecker.EP_NAME, CustomAnnotationChecker.class);
      appEnvironment.addExtension(CustomAnnotationChecker.EP_NAME, new AnnotationCollectorChecker());
      appEnvironment.addExtension(CustomAnnotationChecker.EP_NAME, new BaseScriptAnnotationChecker());
      appEnvironment.addExtension(CustomAnnotationChecker.EP_NAME, new DelegatesToAnnotationChecker());
      appEnvironment.addExtension(CustomAnnotationChecker.EP_NAME, new FieldAnnotationChecker());
      appEnvironment.addExtension(CustomAnnotationChecker.EP_NAME, new GrabAnnotationChecker());
      appEnvironment.addExtension(CustomAnnotationChecker.EP_NAME, new GrAliasAnnotationChecker());
      appEnvironment.addExtension(CustomAnnotationChecker.EP_NAME, new NewifyAnnotationChecker());
      appEnvironment.addExtension(CustomAnnotationChecker.EP_NAME, new TypeCheckedAnnotationChecker());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ImplicitPropertyUsageProvider.EP_NAME,
                                                        ImplicitPropertyUsageProvider.class);
      appEnvironment.addExtension(ImplicitPropertyUsageProvider.EP_NAME, new DGMImplicitPropertyUsageProvider());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ImplicitUsageProvider.EP_NAME,
                                                        ImplicitUsageProvider.class);
      appEnvironment.addExtension(ImplicitUsageProvider.EP_NAME, new GrImplicitUsageProvider());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), FileTypeRegistry.FileTypeDetector.EP_NAME,
                                                        FileTypeRegistry.FileTypeDetector.class);
      appEnvironment.addExtension(FileTypeRegistry.FileTypeDetector.EP_NAME, new GroovyHashBangFileTypeDetector());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ClsCustomNavigationPolicy.EP_NAME,
                                                        ClsCustomNavigationPolicy.class);
      appEnvironment.addExtension(ClsCustomNavigationPolicy.EP_NAME, new GroovyClsCustomNavigationPolicy());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), PomDeclarationSearcher.EP_NAME,
                                                        PomDeclarationSearcher.class);
      appEnvironment.addExtension(PomDeclarationSearcher.EP_NAME, new GebContentDeclarationSearcher());
      appEnvironment.addExtension(PomDeclarationSearcher.EP_NAME, new SpockPomDeclarationSearcher());

      appEnvironment.addExplicitExtension(LanguageConstantExpressionEvaluator.INSTANCE, GroovyLanguage.INSTANCE,
                                          new GroovyConstantExpressionEvaluator());

      appEnvironment.addExplicitExtension(ExpressionConverter.EP, GroovyLanguage.INSTANCE, new GroovyExpressionConverter());
      appEnvironment.addExplicitExtension(LanguageAnnotators.INSTANCE, GroovyLanguage.INSTANCE, new GrAnnotatorImpl());
      appEnvironment.addExplicitExtension(LanguageAnnotators.INSTANCE, GroovyLanguage.INSTANCE, new GroovyDslAnnotator());

      appEnvironment.addExplicitExtension(LanguageStructureViewBuilder.INSTANCE, GroovyLanguage.INSTANCE, new GroovyStructureViewFactory());
      appEnvironment.addExplicitExtension(LanguageFolding.INSTANCE, GroovyLanguage.INSTANCE, new GroovyFoldingBuilder());
      appEnvironment.addExplicitExtension(LanguageFindUsages.INSTANCE, GroovyLanguage.INSTANCE, new GroovyFindUsagesProvider());
      appEnvironment.addExplicitExtension(ReferenceAdjuster.Extension.INSTANCE, GroovyLanguage.INSTANCE, new GrReferenceAdjuster());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), UseScopeEnlarger.EP_NAME, UseScopeEnlarger.class);
      appEnvironment.addExtension(UseScopeEnlarger.EP_NAME, new GrPrivateFieldScopeEnlarger());

      DirectClassInheritorsSearch.INSTANCE.registerExecutor(new GroovyDirectInheritorsSearcher());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), MethodReferencesSearch.EP_NAME, QueryExecutor.class);
      appEnvironment.addExtension(MethodReferencesSearch.EP_NAME, new AccessorMethodReferencesSearcher());
      appEnvironment.addExtension(MethodReferencesSearch.EP_NAME, new MethodLateBoundReferencesSearcher());
      appEnvironment.addExtension(MethodReferencesSearch.EP_NAME, new GroovyConstructorUsagesSearcher());
      appEnvironment.addExtension(MethodReferencesSearch.EP_NAME, new GroovyReflectedMethodReferenceSearcher());
      appEnvironment.addExtension(MethodReferencesSearch.EP_NAME, new GrLiteralMethodSearcher());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), AnnotatedElementsSearch.EP_NAME, QueryExecutor.class);
      appEnvironment.addExtension(AnnotatedElementsSearch.EP_NAME, new AnnotatedMembersSearcher());
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), SuperMethodsSearch.EP_NAME, QueryExecutor.class);
      appEnvironment.addExtension(SuperMethodsSearch.EP_NAME, new GDKSuperMethodSearcher());
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), DefinitionsScopedSearch.EP_NAME, QueryExecutor.class);
      appEnvironment.addExtension(DefinitionsScopedSearch.EP_NAME, new GroovyImplementationSearch());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), StubIndexExtension.EP_NAME, StubIndexExtension.class);
      appEnvironment.addExtension(StubIndexExtension.EP_NAME, new GrAnnotationMethodNameIndex());
      appEnvironment.addExtension(StubIndexExtension.EP_NAME, new GrAnnotatedMemberIndex());
      appEnvironment.addExtension(StubIndexExtension.EP_NAME, new GrAnonymousClassIndex());
      appEnvironment.addExtension(StubIndexExtension.EP_NAME, new GrDirectInheritorsIndex());
      appEnvironment.addExtension(StubIndexExtension.EP_NAME, new GrFieldNameIndex());
      appEnvironment.addExtension(StubIndexExtension.EP_NAME, new GrFullClassNameIndex());
      appEnvironment.addExtension(StubIndexExtension.EP_NAME, new GrFullScriptNameIndex());
      appEnvironment.addExtension(StubIndexExtension.EP_NAME, new GrMethodNameIndex());
      appEnvironment.addExtension(StubIndexExtension.EP_NAME, new GrScriptClassNameIndex());
      appEnvironment.addExtension(StubIndexExtension.EP_NAME, new GrScriptClassNameIndex());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), FileBasedIndexExtension.EXTENSION_POINT_NAME,
                                                        FileBasedIndexExtension.class);
      appEnvironment.addExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, new GroovyDslFileIndex());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ReferencesSearch.EP_NAME, QueryExecutor.class);
      appEnvironment.addExtension(ReferencesSearch.EP_NAME, new ConstructorReferencesSearcher());
      appEnvironment.addExtension(ReferencesSearch.EP_NAME, new GrAliasedImportedElementSearcher());
      appEnvironment.addExtension(ReferencesSearch.EP_NAME, new AccessorReferencesSearcher());
      appEnvironment.addExtension(ReferencesSearch.EP_NAME, new GroovyTraitFieldSearcher());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), FileIconProvider.EP_NAME, FileIconProvider.class);
      appEnvironment.addExtension(FileIconProvider.EP_NAME, new GroovyFileIconProvider());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ResolveScopeProvider.EP_NAME, ResolveScopeProvider.class);
      appEnvironment.addExtension(ResolveScopeProvider.EP_NAME, new GroovyResolveScopeProvider());


      Class<Condition<VirtualFile>> conditionClass = (Class)Condition.class;
      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), WolfTheProblemSolver.FILTER_EP_NAME, conditionClass);
      appEnvironment.addExtension(WolfTheProblemSolver.FILTER_EP_NAME, new GroovyProblemFileHighlightFilter());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ClassTypePointerFactory.EP_NAME, ClassTypePointerFactory.class);
      appEnvironment.addExtension(ClassTypePointerFactory.EP_NAME, new GrClassReferenceTypePointerFactory());

      appEnvironment.registerApplicationService(GroovyQuickFixFactory.class, new EmptyGroovyQuickFixFactory());
      appEnvironment.registerApplicationComponent(DslActivationStatus.class, new DslActivationStatus());

      CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ReadWriteAccessDetector.EP_NAME, ReadWriteAccessDetector.class);
      appEnvironment.addExtension(ReadWriteAccessDetector.EP_NAME, new GroovyReadWriteAccessDetector());
      if (GroovyElementTypes.ADDITIVE_EXPRESSION == null) throw new IllegalStateException(); // initialize tokens
    }

    protected ExternalResourceManagerEx createExternalResourceManager() {
      return new CoreExternalResourceManager();
    }
  }

  public static class ProjectEnvironment {
    public ProjectEnvironment(CoreProjectEnvironment projectEnvironment) {
      MockProject project = projectEnvironment.getProject();
      project.registerService(GroovyPsiElementFactory.class, GroovyPsiElementFactoryImpl.class);

      project.registerService(GroovyPsiManager.class, GroovyPsiManager.class);
      project.registerService(GroovyCodeStyleManager.class, CoreGroovyCodeStyleManager.class);
      project.registerService(GroovyCodeStyleSettingsFacade.class, CoreGroovyCodeStyleSettingsFacade.class);
      project.registerService(GroovyExtensionProvider.class, GroovyExtensionProvider.class);
      projectEnvironment.addProjectExtension(PsiShortNamesCache.EP_NAME, new GroovyShortNamesCache(project));
      projectEnvironment.addProjectExtension(PsiElementFinder.EP_NAME, new GroovyClassFinder(project));
      TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(project);
      projectEnvironment.registerProjectComponent(GroovyUnusedImportsPassFactory.class, new GroovyUnusedImportsPassFactory(project, registrar));
      projectEnvironment.registerProjectComponent(GrKeywordAndDeclarationHighlightFactory.class, new GrKeywordAndDeclarationHighlightFactory(project, registrar));
      projectEnvironment.registerProjectComponent(GrReferenceHighlighterFactory.class, new GrReferenceHighlighterFactory(project, registrar));
    }
  }
}

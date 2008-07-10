/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.CompositeCompletionData;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.grails.GrailsLoader;
import org.jetbrains.plugins.groovy.codeInspection.local.GroovyAddImportsPassFactory;
import org.jetbrains.plugins.groovy.codeInspection.local.GroovyUnusedImportsPassFactory;
import org.jetbrains.plugins.groovy.compiler.generator.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.debugger.GroovyPositionManager;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionData;
import org.jetbrains.plugins.groovy.lang.completion.InsertHandlerRegistry;
import org.jetbrains.plugins.groovy.lang.editor.actions.GroovyEditorActionsManager;
import org.jetbrains.plugins.groovy.lang.groovydoc.completion.GroovyDocCompletionData;
import org.jetbrains.plugins.groovy.lang.groovydoc.completion.handlers.GroovyDocMethodHandler;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.references.GroovyDocReferenceProvider;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.resolve.providers.PropertiesReferenceProvider;

import java.util.HashSet;
import java.util.Set;

/**
 * Main application component, that loads Groovy language support
 *
 * @author ilyas
 */
public class GroovyLoader implements ApplicationComponent {

  @NotNull
  public static final String GROOVY_EXTENSION = "groovy";

  @NotNull
  public static final String GVY_EXTENSION = "gvy";

  @NotNull
  public static final String GY_EXTENSION = "gy";

  @NotNull
  public static final String GROOVY_SCRIPT_EXTENSION = "gsh";

  @NotNull
  public static final Set<String> GROOVY_EXTENSIONS = new HashSet<String>();

  static {
    GROOVY_EXTENSIONS.add(GROOVY_EXTENSION);
    GROOVY_EXTENSIONS.add(GVY_EXTENSION);
    GROOVY_EXTENSIONS.add(GY_EXTENSION);
    GROOVY_EXTENSIONS.add(GROOVY_SCRIPT_EXTENSION);
  }

  public GroovyLoader(GrailsLoader loader) {
  }

  public void initComponent() {
    loadGroovy();
  }

  private static void loadGroovy() {
    //Register factory for special Groovy elements
//todo[DIANA]
//    Factory.addElementFactory(new GspElementFactory());

    //register editor actions
    GroovyEditorActionsManager.registerGroovyEditorActions();

    //Register Keyword completion
    setupCompletion();

    //todo [DIANA] implement me!
//    ReferencesSearch.INSTANCE.registerExecutor(new ConstructorReferencesSearcher());
//    ReferencesSearch.INSTANCE.registerExecutor(new PropertyReferencesSearcher());
//    ReferencesSearch.INSTANCE.registerExecutor(new TypeAliasReferenceSearcher());
//    ReferencesSearch.INSTANCE.registerExecutor(new LateBoundReferencesSearcher());

    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(final Project project) {
        TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(project);
        GroovyUnusedImportsPassFactory unusedImportsPassFactory = project.getComponent(GroovyUnusedImportsPassFactory.class);
        registrar.registerTextEditorHighlightingPass(unusedImportsPassFactory, new int[]{Pass.UPDATE_ALL}, null, true, -1);

        GroovyAddImportsPassFactory addImportsPassFactory = project.getComponent(GroovyAddImportsPassFactory.class);
        registrar.registerTextEditorHighlightingPass(addImportsPassFactory, new int[]{Pass.POPUP_HINTS}, null, true, -1);

        WolfTheProblemSolver.getInstance(project).registerFileHighlightFilter(new Condition<VirtualFile>() {
          public boolean value(VirtualFile virtualFile) {
            return FileTypeManager.getInstance().getFileTypeByFile(virtualFile) == GroovyFileType.GROOVY_FILE_TYPE;
          }
        }, project);

        CompilerManager compilerManager = CompilerManager.getInstance(project);
        GroovyToJavaGenerator generator = new GroovyToJavaGenerator(project);
        compilerManager.addCompiler(generator);
        compilerManager.addCompilationStatusListener(generator);

        compilerManager.addCompilableFileType(GroovyFileType.GROOVY_FILE_TYPE);

        DebuggerManager.getInstance(project).registerPositionManagerFactory(new Function<DebugProcess, PositionManager>() {
          public PositionManager fun(DebugProcess debugProcess) {
            return new GroovyPositionManager(debugProcess);
          }
        });

        //Register Groovydoc reference provider
        ReferenceProvidersRegistry registry = ReferenceProvidersRegistry.getInstance(project);

        registry.registerReferenceProvider(GroovyDocPsiElement.class, new GroovyDocReferenceProvider());
        registry.registerReferenceProvider(GrLiteral.class, new PropertiesReferenceProvider());
      }
    });


    registerNameValidators();
  }

  private static void registerNameValidators() {
// todo [DIANA]
/*
    RenameInputValidatorRegistry.getInstance().registerInputValidator(pattern, new RenameInputValidator(){
      public boolean isInputValid(String newName, PsiElement element, ProcessingContext context) {
        return !GroovyRefactoringUtil.KEYWORDS.contains(newName);
      }
    });
*/
/*
    RenameInputValidatorRegistry.getInstance().registerInputValidator(new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        return element instanceof PsiNamedElement;
      }
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }, new Condition<String>() {
      public boolean value(String name) {
        return !GroovyRefactoringUtil.KEYWORDS.contains(name);
      }
    });
*/
  }

  private static void setupCompletion() {
    InsertHandlerRegistry handlerRegistry = InsertHandlerRegistry.getInstance();
    handlerRegistry.registerSpecificInsertHandler(new GroovyDocMethodHandler());

    CompositeCompletionData compositeCompletionData = new CompositeCompletionData(new GroovyCompletionData(), new GroovyDocCompletionData());
    CompletionUtil.registerCompletionData(GroovyFileType.GROOVY_FILE_TYPE, compositeCompletionData);
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "groovy.support.loader";
  }

}

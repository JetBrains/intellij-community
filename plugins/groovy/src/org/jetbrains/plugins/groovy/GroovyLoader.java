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
import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertiesReferenceProvider;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.toolPanel.DynamicToolWindowUtil;
import org.jetbrains.plugins.groovy.codeInspection.local.GroovyUnusedImportsPassFactory;
import org.jetbrains.plugins.groovy.compiler.GroovyCompiler;
import org.jetbrains.plugins.groovy.compiler.generator.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.debugger.GroovyPositionManager;
import org.jetbrains.plugins.groovy.findUsages.*;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionData;
import org.jetbrains.plugins.groovy.lang.editor.GroovyQuoteHandler;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.resolve.providers.PropertiesReferenceProvider;
import org.jetbrains.plugins.groovy.editor.GroovyLiteralSelectioner;
import org.jetbrains.plugins.grails.GrailsLoader;

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
    ApplicationManager.getApplication().runWriteAction(
        new Runnable() {
          public void run() {
            FileTypeManager.getInstance().registerFileType(GroovyFileType.GROOVY_FILE_TYPE, GROOVY_EXTENSIONS.toArray(new String[GROOVY_EXTENSIONS.size()]));
          }
        }
    );

    CompletionUtil.registerCompletionData(GroovyFileType.GROOVY_FILE_TYPE, new GroovyCompletionData());

    SelectWordUtil.registerSelectioner(new GroovyLiteralSelectioner());

    MethodReferencesSearch.INSTANCE.registerExecutor(new AccessorReferencesSearcher());
    MethodReferencesSearch.INSTANCE.registerExecutor(new MethodLateBoundReferencesSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new ConstructorReferencesSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new PropertyReferencesSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new TypeAliasReferenceSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new LateBoundReferencesSearcher());

    TypedHandler.registerQuoteHandler(GroovyFileType.GROOVY_FILE_TYPE, new GroovyQuoteHandler());

    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(final Project project) {
        TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(project);
        GroovyUnusedImportsPassFactory unusedImportsPassFactory = project.getComponent(GroovyUnusedImportsPassFactory.class);
        registrar.registerTextEditorHighlightingPass(unusedImportsPassFactory, new int[]{Pass.UPDATE_ALL}, null, true, -1);

        WolfTheProblemSolver.getInstance(project).registerFileHighlightFilter(new Condition<VirtualFile>() {
          public boolean value(VirtualFile virtualFile) {
            return FileTypeManager.getInstance().getFileTypeByFile(virtualFile) == GroovyFileType.GROOVY_FILE_TYPE;
          }
        }, project);

        CompilerManager compilerManager = CompilerManager.getInstance(project);
        GroovyToJavaGenerator generator = new GroovyToJavaGenerator(project);
        compilerManager.addCompiler(generator);
        compilerManager.addCompilationStatusListener(generator);

        compilerManager.addCompiler(new GroovyCompiler(project));
        compilerManager.addCompilableFileType(GroovyFileType.GROOVY_FILE_TYPE);

        DebuggerManager.getInstance(project).registerPositionManagerFactory(new Function<DebugProcess, PositionManager>() {
          public PositionManager fun(DebugProcess debugProcess) {
            return new GroovyPositionManager(debugProcess);
          }
        });

        ReferenceProvidersRegistry.getInstance(project).registerReferenceProvider(GrLiteral.class, new PropertiesReferenceProvider());
        ReferenceProvidersRegistry.getInstance(project).registerReferenceProvider(GrReferenceExpression.class, new DynamicPropertiesReferenceProvider());

        StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
          public void run() {
            final ToolWindow dynamicToolWindow = ToolWindowManager.getInstance(project).registerToolWindow(DynamicToolWindowUtil.DYNAMIC_TOOLWINDOW_ID, true, ToolWindowAnchor.RIGHT);
            dynamicToolWindow.setIcon(IconLoader.getIcon("/org/jetbrains/plugins/groovy/images/dynamicProperty.png"));

            DynamicToolWindowUtil.setUpDynamicToolWindow(project, dynamicToolWindow);
          }
        });


      }
    });
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "groovy.support.loader";
  }
}
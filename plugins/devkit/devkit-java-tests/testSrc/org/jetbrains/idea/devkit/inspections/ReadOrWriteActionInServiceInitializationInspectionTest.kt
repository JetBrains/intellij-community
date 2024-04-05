// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

class ReadOrWriteActionInServiceInitializationInspectionTest : ReadOrWriteActionInServiceInitializationInspectionTestBase() {

  fun `test read and write actions are reported in a light service`() {
    myFixture.configureByText("TestService.java", getServiceWithReadAndWriteActionsCalledDuringInit(true))
    myFixture.checkHighlighting()
  }

  fun `test read and write actions are reported in an XML-registered service`() {
    myFixture.addFileToProject(
      "META-INF/plugin.xml",
      // language=XML
      """
      <idea-plugin>
        <extensions defaultExtensionNs="com.intellij">
          <applicationService serviceImplementation="TestService"/>
        </extensions>
      </idea-plugin>
    """.trimIndent())
    myFixture.configureByText("TestService.java", getServiceWithReadAndWriteActionsCalledDuringInit(false))
    myFixture.checkHighlighting()
  }

  private fun getServiceWithReadAndWriteActionsCalledDuringInit(lightService: Boolean): String {
    // language=java
    return """
      import com.intellij.openapi.application.ActionsKt;
      import com.intellij.openapi.application.Application;
      import com.intellij.openapi.application.ApplicationManager;
      import com.intellij.openapi.application.ReadAction;
      import com.intellij.openapi.application.ReadAction.CannotReadException;
      import com.intellij.openapi.application.WriteAction;
      ${if (lightService) "import com.intellij.openapi.components.Service;" else ""}
      import com.intellij.openapi.util.ThrowableComputable;
      import static com.intellij.openapi.application.ActionsKt.runReadAction;
      
      ${if (lightService) "@Service" else ""}
      class TestService {
      
        String v1 = ReadAction.<error descr="Do not run read actions during service initialization">compute</error>(() -> {return "";});
        static final String v2 = ReadAction.<error descr="Do not run read actions during service initialization">computeCancellable</error>(() -> {return "";});
      
        String v3 = getV3();
        private String getV3() {
          return ReadAction.<error descr="Do not run read actions during service initialization">compute</error>(() -> {return "";});
        }
        
        static final String v4 = getV4();
        private static String getV4() {
          return ReadAction.<error descr="Do not run read actions during service initialization">computeCancellable</error>(() -> {return "";});
        }
      
        static {
          ApplicationManager.getApplication().<error descr="Do not run read actions during service initialization">runReadAction</error>(() -> {
            // do something
          });
          ApplicationManager.getApplication().<error descr="Do not run write actions during service initialization">runWriteAction</error>(() -> {
            // do something
          });
          writeActionMethodUsedInStaticInitBlock();
        }
        
        private static void writeActionMethodUsedInStaticInitBlock() {
          WriteAction.<error descr="Do not run write actions during service initialization">run</error>(() -> {});
        }
      
        TestService() {
          <error descr="Do not run read actions during service initialization">runReadAction</error>(() -> null);
          ActionsKt.<error descr="Do not run write actions during service initialization">runWriteAction</error>(() -> null);
          readActionMethodUsedInConstructor();
        }
        
        private void readActionMethodUsedInConstructor() {
          ReadAction.nonBlocking(() -> "")
                     .<error descr="Do not run read actions during service initialization">executeSynchronously</error>();
        }
        
        public void notUsedInInit() {
          // should not be reported:
          ApplicationManager.getApplication().runReadAction(() -> {
            // do something
          });
        }
        
        public static void notUsedInInitStatic() {
          // should not be reported:
          ApplicationManager.getApplication().runWriteAction(() -> {
            // do something
          });
        }
      }
      """.trimIndent()
  }

  fun `test read and write actions are not reported in a non-service class`() {
    myFixture.configureByText(
      "TestService.java",
      // language=java
      """
      import com.intellij.openapi.application.ActionsKt;
      import com.intellij.openapi.application.Application;
      import com.intellij.openapi.application.ApplicationManager;
      import com.intellij.openapi.application.ReadAction;
      import com.intellij.openapi.application.ReadAction.CannotReadException;
      import com.intellij.openapi.application.WriteAction;
      import com.intellij.openapi.util.ThrowableComputable;
      import static com.intellij.openapi.application.ActionsKt.runReadAction;
      
      class NotAService {
      
        String v1 = ReadAction.compute(() -> {return "";});
        static final String v2 = ReadAction.computeCancellable(() -> {return "";});
      
        String v3 = getV3();
        private String getV3() {
          return ReadAction.compute(() -> {return "";});
        }
        
        static final String v4 = getV4();
        private static String getV4() {
          return ReadAction.computeCancellable(() -> {return "";});
        }
      
        static {
          ApplicationManager.getApplication().runReadAction(() -> {
            // do something
          });
          ApplicationManager.getApplication().runWriteAction(() -> {
            // do something
          });
          writeActionMethodUsedInStaticInitBlock();
        }
        
        private static void writeActionMethodUsedInStaticInitBlock() {
          WriteAction.run(() -> {});
        }
      
        NotAService() {
          runReadAction(() -> null);
          ActionsKt.runWriteAction(() -> null);
          readActionMethodUsedInConstructor();
        }
        
        private void readActionMethodUsedInConstructor() {
          ReadAction.nonBlocking(() -> "").executeSynchronously();
        }
        
        public void notUsedInInit() {
          // should not be reported:
          ApplicationManager.getApplication().runReadAction(() -> {
            // do something
          });
        }
        
        public static void notUsedInInitStatic() {
          // should not be reported:
          ApplicationManager.getApplication().runWriteAction(() -> {
            // do something
          });
        }
      }
      """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

  fun `test read and write actions are reported in an PersistentStateComponent lifecycle methods`() {
    myFixture.configureByText(
      "TestSettings.java",
      // language=java
      """
      import com.intellij.openapi.application.ActionsKt;
      import com.intellij.openapi.application.Application;
      import com.intellij.openapi.application.ApplicationManager;
      import com.intellij.openapi.application.ReadAction;
      import com.intellij.openapi.application.WriteAction;
      import com.intellij.openapi.components.Service;
      import com.intellij.openapi.util.ThrowableComputable;
      import com.intellij.openapi.components.PersistentStateComponent;
      import com.intellij.openapi.components.Service;
      import com.intellij.openapi.components.State;
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.Nullable;
      import static com.intellij.openapi.application.ActionsKt.runReadAction;
      
      @Service(Service.Level.PROJECT)
      @State(name = "TestSettings")
      final class TestSettings implements PersistentStateComponent<TestSettings.State> {
      
        static final class State {
          public boolean testValue = true;
        }
      
        private State myState = new State();
      
        @Override
        public void loadState(@NotNull State state) {
          String value = ReadAction.<error descr="Do not run read actions during service initialization">compute</error>(() -> {return "";});
          myState = state;
        }
      
        @Override public void initializeComponent() {
          ReadAction.<error descr="Do not run read actions during service initialization">compute</error>(() -> {return "";});
        }
        
        @Override public void noStateLoaded() {
          WriteAction.<error descr="Do not run write actions during service initialization">run</error>(() -> {});
        }
      
        @Override
        public @Nullable State getState() {
          // read/write actions are allowed in getState:
          ApplicationManager.getApplication().runReadAction(() -> {
            // do something
          });
          ApplicationManager.getApplication().runWriteAction(() -> {
            // do something
          });
          return myState;
        }
      }
      """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun `test read and write actions are not reported in an object literals and lambdas`() {
    myFixture.configureByText(
      "TestService.java",
      // language=java
      """
      import com.intellij.openapi.application.ActionsKt;
      import com.intellij.openapi.application.ApplicationManager;
      import com.intellij.openapi.application.ReadAction;
      import com.intellij.openapi.components.Service;
      import com.intellij.openapi.project.Project;
      import com.intellij.openapi.project.ProjectCloseListener;
      import com.intellij.util.Alarm;
      import org.jetbrains.annotations.NotNull;
      import static com.intellij.openapi.application.ActionsKt.runReadAction;
      
      @Service
      class TestService {
      
        private final ProjectCloseListener myListener1;
        private final ProjectCloseListener myListener2;
        private final Alarm myAlarm = new Alarm();
      
        TestService() {
          myListener1 = new ProjectCloseListener() {
            @Override
            public void projectClosed(@NotNull Project project) {
              readActionMethodUsedInConstructor();
            }
          };
          myListener2 = new ProjectCloseListener() {
            @Override
            public void projectClosed(@NotNull Project project) {
              runReadAction(() -> null);
            }
          };
          myAlarm.addRequest(() -> {
            ActionsKt.runWriteAction(() -> null);
          }, 100);
        }
        
        private static void readActionMethodUsedInConstructor() {
          ReadAction.nonBlocking(() -> "").executeSynchronously();
        }
      }
      """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

}

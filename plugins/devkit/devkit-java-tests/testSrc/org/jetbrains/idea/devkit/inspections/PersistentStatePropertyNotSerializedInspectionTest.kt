// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

class PersistentStatePropertyNotSerializedInspectionTest : PersistentStatePropertyNotSerializedInspectionTestBase() {

  fun `test final field of a state class is reported`() {
    doTest("MySettings.java", """
      import com.intellij.openapi.components.PersistentStateComponent;
      import com.intellij.openapi.components.State;
      import com.intellij.openapi.components.Storage;

      @State(name = "MySettings", storages = @Storage("my.xml"))
      final class MySettings implements PersistentStateComponent<MyState> {
        private MyState myState = new MyState();

        @Override
        public MyState getState() {
          return myState;
        }

        @Override
        public void loadState(MyState state) {
          myState = state;
        }
      }

      class MyState {
        public final boolean <warning descr="Add a store annotation to the field 'showTimestamp', or make it mutable, so the store saves it">showTimestamp</warning> = true;
        public boolean showSource = true;
        public static final String DEFAULT_FORMAT = "text";
      }
      """)
  }

  fun `test field that is not public is reported`() {
    doTest("MyState.java", """
      import com.intellij.openapi.components.BaseState;

      class MyState extends BaseState {
        final boolean <warning descr="Make the property 'showTimestamp' public, or add a store annotation to its field, so the store saves it">showTimestamp</warning> = true;
        boolean <warning descr="Make the property 'showSource' public, or add a store annotation to its field, so the store saves it">showSource</warning> = true;
        protected boolean <warning descr="Make the property 'showLevel' public, or add a store annotation to its field, so the store saves it">showLevel</warning> = true;
        private boolean showThread = true;
      }
      """)
  }

  fun `test property that has no setter is reported`() {
    doTest("MyState.java", """
      import com.intellij.openapi.components.BaseState;

      class MyState extends BaseState {
        private boolean <warning descr="Add a store annotation to the field 'showTimestamp', or make it mutable, so the store saves it">showTimestamp</warning> = true;
        private boolean showSource = true;

        public boolean isShowTimestamp() {
          return showTimestamp;
        }

        public boolean isShowSource() {
          return showSource;
        }

        public void setShowSource(boolean showSource) {
          this.showSource = showSource;
        }
      }
      """)
  }

  fun `test store annotation keeps a final field`() {
    doTest("MyState.java", """
      import com.intellij.openapi.components.BaseState;
      import com.intellij.util.xmlb.annotations.Attribute;
      import com.intellij.util.xmlb.annotations.OptionTag;

      class MyState extends BaseState {
        @OptionTag public final boolean showTimestamp = true;
        @Attribute final boolean showSource = true;
        @com.intellij.configurationStore.Property public final boolean <warning descr="Add a store annotation to the field 'showLevel', or make it mutable, so the store saves it">showLevel</warning> = true;
      }
      """)
  }

  fun `test transient field is not reported`() {
    doTest("MyState.java", """
      import com.intellij.openapi.components.BaseState;
      import com.intellij.util.xmlb.annotations.Transient;

      class MyState extends BaseState {
        @Transient public final boolean showTimestamp = true;
        public transient final boolean showSource = true;
      }
      """)
  }

  fun `test final collection field is not reported`() {
    doTest("MyState.java", """
      import com.intellij.openapi.components.BaseState;
      import com.intellij.util.xmlb.annotations.XCollection;
      import java.util.ArrayList;
      import java.util.List;

      class MyState extends BaseState {
        public final List<String> fields = new ArrayList<String>();
        private final List<String> levels = new ArrayList<String>();

        @XCollection
        public List<String> getLevels() {
          return levels;
        }
      }
      """)
  }

  fun `test field of a type with a required constructor parameter is not reported`() {
    doTest("MyState.java", """
      import com.intellij.openapi.components.BaseState;

      class MyState extends BaseState {
        public final Wrapper wrapper = new Wrapper("text");
      }

      class Wrapper {
        public String value;

        public Wrapper(String value) {
          this.value = value;
        }
      }
      """)
  }

  fun `test field of a type with a no-arg constructor is reported`() {
    doTest("MyState.java", """
      import com.intellij.openapi.components.BaseState;

      class MyState extends BaseState {
        public final Wrapper <warning descr="Add a store annotation to the field 'wrapper', or make it mutable, so the store saves it">wrapper</warning> = new Wrapper();
      }

      class Wrapper {
        public Wrapper() {
        }
      }
      """)
  }

  fun `test field of a type with an overloaded no-arg constructor is reported`() {
    doTest("MyState.java", """
      import com.intellij.openapi.components.BaseState;

      class MyState extends BaseState {
        public final Wrapper <warning descr="Add a store annotation to the field 'wrapper', or make it mutable, so the store saves it">wrapper</warning> = new Wrapper();
      }

      class Wrapper {
        public String value;

        public Wrapper() {
          this("text");
        }

        public Wrapper(String value) {
          this.value = value;
        }
      }
      """)
  }

  fun `test field of a class that is not a state is not reported`() {
    doTest("MyService.java", """
      import java.util.ArrayList;
      import java.util.List;

      final class MyService {
        public final boolean showTimestamp = true;
        private final Object lock = new Object();
        private final List<String> cache = new ArrayList<String>();
      }
      """)
  }

  fun `test fix adds a store annotation to a final field`() {
    doFixTest("MyState.java", """
      import com.intellij.openapi.components.BaseState;

      class MyState extends BaseState {
        public final boolean <warning descr="Add a store annotation to the field 'showTimestamp', or make it mutable, so the store saves it">show<caret>Timestamp</warning> = true;
      }
      """, "Annotate field 'showTimestamp' as '@Property'", """
      import com.intellij.openapi.components.BaseState;
      import com.intellij.util.xmlb.annotations.Property;

      class MyState extends BaseState {
          @Property
          public final boolean showTimestamp = true;
      }
      """)
  }

  fun `test fix adds a store annotation to a field that is not public`() {
    doFixTest("MyState.java", """
      import com.intellij.openapi.components.BaseState;

      class MyState extends BaseState {
        boolean <warning descr="Make the property 'showSource' public, or add a store annotation to its field, so the store saves it">show<caret>Source</warning> = true;
      }
      """, "Annotate field 'showSource' as '@Property'", """
      import com.intellij.openapi.components.BaseState;
      import com.intellij.util.xmlb.annotations.Property;

      class MyState extends BaseState {
          @Property
          boolean showSource = true;
      }
      """)
  }

  fun `test fix adds XCollection to a collection field`() {
    doFixTest("MyState.java", """
      import com.intellij.openapi.components.BaseState;
      import java.util.ArrayList;
      import java.util.List;

      class MyState extends BaseState {
        List<String> <warning descr="Make the property 'names' public, or add a store annotation to its field, so the store saves it">na<caret>mes</warning> = new ArrayList<String>();
      }
      """, "Annotate field 'names' as '@XCollection'", """
      import com.intellij.openapi.components.BaseState;
      import com.intellij.util.xmlb.annotations.XCollection;

      import java.util.ArrayList;
      import java.util.List;

      class MyState extends BaseState {
          @XCollection
          List<String> names = new ArrayList<String>();
      }
      """)
  }

  fun `test fix adds XMap to a map field`() {
    doFixTest("MyState.java", """
      import com.intellij.openapi.components.BaseState;
      import java.util.HashMap;
      import java.util.Map;

      class MyState extends BaseState {
        Map<String, String> <warning descr="Make the property 'levels' public, or add a store annotation to its field, so the store saves it">le<caret>vels</warning> = new HashMap<String, String>();
      }
      """, "Annotate field 'levels' as '@XMap'", """
      import com.intellij.openapi.components.BaseState;
      import com.intellij.util.xmlb.annotations.XMap;

      import java.util.HashMap;
      import java.util.Map;

      class MyState extends BaseState {
          @XMap
          Map<String, String> levels = new HashMap<String, String>();
      }
      """)
  }

  private fun doTest(fileName: String, text: String) {
    myFixture.configureByText(fileName, text.trimIndent().trim())
    myFixture.checkHighlighting()
  }

  private fun doFixTest(fileName: String, before: String, fixName: String, after: String) {
    myFixture.configureByText(fileName, before.trimIndent().trim())
    myFixture.checkHighlighting()
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(fixName))
    myFixture.checkResult(after.trimIndent().trim(), true)
    // The inspection repeats the rules of the store, so no warning shows that the annotation reached the field.
    myFixture.checkHighlighting()
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.structuralsearch.inspection.highlightTemplate.SSBasedInspection;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class SSRSerializationTest extends CodeInsightFixtureTestCase {

  private TestInspectionProfile myProfile;
  private SSBasedInspection myInspection;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("ssr.separate.inspections").setValue(true, getTestRootDisposable());
    myInspection = new SSBasedInspection();
    final SearchConfiguration configuration1 = new SearchConfiguration("i", "user defined");
    final MatchOptions options = configuration1.getMatchOptions();
    options.setFileType(StdFileTypes.JAVA);
    options.setSearchPattern("int i;");
    myInspection.setConfigurations(Arrays.asList(configuration1));
    final InspectionToolsSupplier supplier = new InspectionToolsSupplier() {

      @Override
      public @NotNull List<InspectionToolWrapper> createTools() {
        return Arrays.asList(new LocalInspectionToolWrapper(myInspection) {
          @Override
          public boolean isEnabledByDefault() {
            return true; // this should no longer be necessary when SSBasedInspection is enabled by default.
          }
        });
      }
    };
    myProfile = new TestInspectionProfile(supplier);
    myProfile.initialize(getProject());
  }

  private String buildXmlFromProfile() {
    final Element node = new Element("entry");
    myProfile.writeExternal(node);
    return JDOMUtil.writeElement(node);
  }

  public void testSimple() {
    final List<Tools> tools = myProfile.getAllEnabledInspectionTools(getProject());
    assertEquals("SSBasedInspection and 1 child tool should be enabled", 2, tools.size());
  }

  public void testDefaultToolsNotWritten() {
    final String expected = "<entry version=\"1.0\">\n" +
                            "  <option name=\"myName\" value=\"test\" />\n" +
                            "  <inspection_tool class=\"SSBasedInspection\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                            "    <searchConfiguration name=\"i\" text=\"int i;\" recursive=\"false\" caseInsensitive=\"false\" type=\"JAVA\" />\n" +
                            "  </inspection_tool>\n" +
                            "</entry>";
    assertEquals(expected, buildXmlFromProfile());
  }

  public void testModifiedToolShouldBeWritten() {
    final Configuration configuration = myInspection.getConfigurations().get(0);
    myProfile.setToolEnabled(configuration.getUuid().toString(), false);

    final String expected =
      "<entry version=\"1.0\">\n" +
      "  <option name=\"myName\" value=\"test\" />\n" +
      "  <inspection_tool class=\"865c0c0b-4ab0-3063-a5ca-a3387c1a8741\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
      "  <inspection_tool class=\"SSBasedInspection\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
      "    <searchConfiguration name=\"i\" text=\"int i;\" recursive=\"false\" caseInsensitive=\"false\" type=\"JAVA\" />\n" +
      "  </inspection_tool>\n" +
      "</entry>";
    assertEquals(expected, buildXmlFromProfile());
  }

  public void testWriteUuidWhenNameChanged() {
    final Configuration configuration = myInspection.getConfigurations().get(0);
    configuration.setName("j");

    final String expected =
      "<entry version=\"1.0\">\n" +
      "  <option name=\"myName\" value=\"test\" />\n" +
      "  <inspection_tool class=\"SSBasedInspection\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
      "    <searchConfiguration name=\"j\" uuid=\"865c0c0b-4ab0-3063-a5ca-a3387c1a8741\" text=\"int i;\" recursive=\"false\" caseInsensitive=\"false\" type=\"JAVA\" />\n" +
      "  </inspection_tool>\n" +
      "</entry>";
    assertEquals(expected, buildXmlFromProfile());
  }

  private static class TestInspectionProfile extends InspectionProfileImpl {

    TestInspectionProfile(InspectionToolsSupplier supplier) {
      super("test", supplier, (BaseInspectionProfileManager)InspectionProfileManager.getInstance());
    }

    @Override
    public void initialize(@Nullable Project project) {
      super.initialize(project);
    }
  }
}

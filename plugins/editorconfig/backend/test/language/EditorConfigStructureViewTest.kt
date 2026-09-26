// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language

import com.intellij.testFramework.PlatformTestUtil.assertTreeEqual
import com.intellij.testFramework.PlatformTestUtil.expandAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditorConfigStructureViewTest : BasePlatformTestCase() {
  override fun getBasePath() =
    "/plugins/editorconfig/testData/org/editorconfig/language/structureview/"

  override fun isCommunity(): Boolean = true

  fun doTest(expected: String) {
    myFixture.configureByFile(getTestName(true) + "/.editorconfig")
    myFixture.testStructureView { svc ->
      expandAll(svc.tree)
      assertTreeEqual(svc.tree, expected)
    }
  }


  fun testSingleSection() {
    val expected =
      """-.editorconfig
        | -[foo]
        |  key""".trimMargin()
    doTest(expected)
  }

  fun testRootDeclaration() {
    val expected =
      """-.editorconfig
        | root = true
        | -[foo]
        |  key
        |  another_key""".trimMargin()
    doTest(expected)
  }

  fun testQualifiedKeys() {
    val expected =
      """-.editorconfig
        | -[foo]
        |  hello.world.one
        |  charset
        |""".trimMargin()
    doTest(expected)
  }

  fun testRealWorldExample() {
    val expected =
      """-.editorconfig
        | root = true
        | -[*]
        |  indent_style
        | -[*.{cs,csx,vb,vbx}]
        |  indent_size
        |  insert_final_newline
        |  charset
        | -[*.{csproj,vbproj,vcxproj,vcxproj.filters,proj,projitems,shproj}]
        |  indent_size
        | -[*.{props,targets,ruleset,config,nuspec,resx,vsixmanifest,vsct}]
        |  indent_size
        | -[*.json]
        |  indent_size
        | -[*.{cs,vb}]
        |  dotnet_sort_system_directives_first
        |  dotnet_style_qualification_for_field
        |  dotnet_style_qualification_for_property
        |  dotnet_style_qualification_for_method
        |  dotnet_style_qualification_for_event
        |  dotnet_style_predefined_type_for_locals_parameters_members
        |  dotnet_style_predefined_type_for_member_access
        |  dotnet_style_object_initializer
        |  dotnet_style_collection_initializer
        |  dotnet_style_coalesce_expression
        |  dotnet_style_null_propagation
        |  dotnet_style_explicit_tuple_names
        | -[*.cs]
        |  csharp_indent_block_contents
        |  csharp_indent_braces
        |  csharp_indent_case_contents
        |  csharp_indent_switch_labels
        |  csharp_indent_labels
        |  csharp_style_var_for_built_in_types
        |  csharp_style_var_when_type_is_apparent
        |  csharp_style_var_elsewhere
        |  csharp_style_expression_bodied_methods
        |  csharp_style_expression_bodied_constructors
        |  csharp_style_expression_bodied_operators
        |  csharp_style_expression_bodied_properties
        |  csharp_style_expression_bodied_indexers
        |  csharp_style_expression_bodied_accessors
        |  csharp_style_pattern_matching_over_is_with_cast_check
        |  csharp_style_pattern_matching_over_as_with_null_check
        |  csharp_style_inlined_variable_declaration
        |  csharp_style_throw_expression
        |  csharp_style_conditional_delegate_call
        |  csharp_new_line_before_open_brace
        |  csharp_new_line_before_else
        |  csharp_new_line_before_catch
        |  csharp_new_line_before_finally
        |  csharp_new_line_before_members_in_object_initializers
        |  csharp_new_line_before_members_in_anonymous_types""".trimMargin()
    doTest(expected)
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This is a generated file. Not intended for manual editing.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyParserUtils.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyGeneratedParserUtils.adapt_builder_;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class GroovyGeneratedParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, EXTENDS_SETS_);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    if (t == ANNOTATION) {
      r = annotation(b, 0);
    }
    else if (t == ANNOTATION_ARGUMENT_LIST) {
      r = annotation_argument_list(b, 0);
    }
    else if (t == ANNOTATION_ARRAY_VALUE) {
      r = annotation_array_value(b, 0);
    }
    else if (t == ANNOTATION_MEMBER_VALUE_PAIR) {
      r = annotation_member_value_pair(b, 0);
    }
    else if (t == ANNOTATION_METHOD) {
      r = annotation_method(b, 0);
    }
    else if (t == ANNOTATION_TYPE_DEFINITION) {
      r = annotation_type_definition(b, 0);
    }
    else if (t == ANONYMOUS_TYPE_DEFINITION) {
      r = anonymous_type_definition(b, 0);
    }
    else if (t == APPLICATION_ARGUMENT_LIST) {
      r = application_argument_list(b, 0);
    }
    else if (t == APPLICATION_EXPRESSION) {
      r = application_expression(b, 0);
    }
    else if (t == APPLICATION_INDEX) {
      r = application_index(b, 0);
    }
    else if (t == ARGUMENT_LABEL) {
      r = argument_label(b, 0);
    }
    else if (t == ARRAY_DECLARATION) {
      r = array_declaration(b, 0);
    }
    else if (t == ARRAY_TYPE_ELEMENT) {
      r = array_type_element(b, 0);
    }
    else if (t == ASSERT_STATEMENT) {
      r = assert_statement(b, 0);
    }
    else if (t == BLOCK_STATEMENT) {
      r = block_statement(b, 0);
    }
    else if (t == BREAK_STATEMENT) {
      r = break_statement(b, 0);
    }
    else if (t == CASE_LABEL) {
      r = case_label(b, 0);
    }
    else if (t == CASE_SECTION) {
      r = case_section(b, 0);
    }
    else if (t == CATCH_CLAUSE) {
      r = catch_clause(b, 0);
    }
    else if (t == CLASS_BODY) {
      r = class_body(b, 0);
    }
    else if (t == CLASS_INITIALIZER) {
      r = class_initializer(b, 0);
    }
    else if (t == CLASS_TYPE_DEFINITION) {
      r = class_type_definition(b, 0);
    }
    else if (t == CLASS_TYPE_ELEMENT) {
      r = class_type_element(b, 0);
    }
    else if (t == CLOSURE) {
      r = closure(b, 0);
    }
    else if (t == CODE_REFERENCE) {
      r = code_reference(b, 0);
    }
    else if (t == CONSTRUCTOR) {
      r = constructor(b, 0);
    }
    else if (t == CONSTRUCTOR_BLOCK) {
      r = constructor_block(b, 0);
    }
    else if (t == CONSTRUCTOR_CALL_EXPRESSION) {
      r = constructor_call_expression(b, 0);
    }
    else if (t == CONTINUE_STATEMENT) {
      r = continue_statement(b, 0);
    }
    else if (t == DISJUNCTION_TYPE_ELEMENT) {
      r = disjunction_type_element(b, 0);
    }
    else if (t == DO_WHILE_STATEMENT) {
      r = do_while_statement(b, 0);
    }
    else if (t == DOLLAR_SLASHY_LITERAL) {
      r = dollar_slashy_literal(b, 0);
    }
    else if (t == ENUM_BODY) {
      r = enum_body(b, 0);
    }
    else if (t == ENUM_CONSTANT) {
      r = enum_constant(b, 0);
    }
    else if (t == ENUM_CONSTANT_INITIALIZER) {
      r = enum_constant_initializer(b, 0);
    }
    else if (t == ENUM_CONSTANTS) {
      r = enum_constants(b, 0);
    }
    else if (t == ENUM_TYPE_DEFINITION) {
      r = enum_type_definition(b, 0);
    }
    else if (t == EXPRESSION) {
      r = expression(b, 0, -1);
    }
    else if (t == FIELD) {
      r = field(b, 0);
    }
    else if (t == FINALLY_CLAUSE) {
      r = finally_clause(b, 0);
    }
    else if (t == FOR_IN_CLAUSE) {
      r = for_in_clause(b, 0);
    }
    else if (t == FOR_STATEMENT) {
      r = for_statement(b, 0);
    }
    else if (t == IF_STATEMENT) {
      r = if_statement(b, 0);
    }
    else if (t == IMPORT) {
      r = import_$(b, 0);
    }
    else if (t == IMPORT_ALIAS) {
      r = import_alias(b, 0);
    }
    else if (t == INTERFACE_TYPE_DEFINITION) {
      r = interface_type_definition(b, 0);
    }
    else if (t == LABELED_STATEMENT) {
      r = labeled_statement(b, 0);
    }
    else if (t == LEFT_SHIFT_SIGN) {
      r = left_shift_sign(b, 0);
    }
    else if (t == METHOD) {
      r = method(b, 0);
    }
    else if (t == MODIFIER_LIST) {
      r = modifier_list(b, 0);
    }
    else if (t == NAMED_ARGUMENT) {
      r = named_argument(b, 0);
    }
    else if (t == OPEN_BLOCK) {
      r = open_block(b, 0);
    }
    else if (t == PACKAGE_DEFINITION) {
      r = package_definition(b, 0);
    }
    else if (t == PARAMETER) {
      r = parameter(b, 0);
    }
    else if (t == PRIMITIVE_TYPE_ELEMENT) {
      r = primitive_type_element(b, 0);
    }
    else if (t == RETURN_STATEMENT) {
      r = return_statement(b, 0);
    }
    else if (t == RIGHT_SHIFT_SIGN) {
      r = right_shift_sign(b, 0);
    }
    else if (t == RIGHT_SHIFT_UNSIGNED_SIGN) {
      r = right_shift_unsigned_sign(b, 0);
    }
    else if (t == SLASHY_LITERAL) {
      r = slashy_literal(b, 0);
    }
    else if (t == SPREAD_LIST_ARGUMENT) {
      r = spread_list_argument(b, 0);
    }
    else if (t == STRING_INJECTION) {
      r = string_injection(b, 0);
    }
    else if (t == SWITCH_STATEMENT) {
      r = switch_statement(b, 0);
    }
    else if (t == SYNCHRONIZED_STATEMENT) {
      r = synchronized_statement(b, 0);
    }
    else if (t == THROW_STATEMENT) {
      r = throw_statement(b, 0);
    }
    else if (t == TRADITIONAL_FOR_CLAUSE) {
      r = traditional_for_clause(b, 0);
    }
    else if (t == TRAIT_TYPE_DEFINITION) {
      r = trait_type_definition(b, 0);
    }
    else if (t == TRY_RESOURCE_LIST) {
      r = try_resource_list(b, 0);
    }
    else if (t == TRY_STATEMENT) {
      r = try_statement(b, 0);
    }
    else if (t == TUPLE) {
      r = tuple(b, 0);
    }
    else if (t == TYPE_ARGUMENT_LIST) {
      r = type_argument_list(b, 0);
    }
    else if (t == TYPE_ELEMENT) {
      r = type_element(b, 0);
    }
    else if (t == TYPE_PARAMETER) {
      r = type_parameter(b, 0);
    }
    else if (t == TYPE_PARAMETER_BOUNDS_LIST) {
      r = type_parameter_bounds_list(b, 0);
    }
    else if (t == TYPE_PARAMETER_LIST) {
      r = type_parameter_list(b, 0);
    }
    else if (t == VARIABLE) {
      r = variable(b, 0);
    }
    else if (t == WHILE_STATEMENT) {
      r = while_statement(b, 0);
    }
    else if (t == WILDCARD_TYPE_ELEMENT) {
      r = wildcard_type_element(b, 0);
    }
    else {
      r = parse_root_(t, b, 0);
    }
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return root(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ARRAY_TYPE_ELEMENT, CLASS_TYPE_ELEMENT, DISJUNCTION_TYPE_ELEMENT, PRIMITIVE_TYPE_ELEMENT,
      TYPE_ELEMENT, WILDCARD_TYPE_ELEMENT),
    create_token_set_(ADDITIVE_EXPRESSION, APPLICATION_EXPRESSION, ASSIGNMENT_EXPRESSION, AS_EXPRESSION,
      BAND_EXPRESSION, BOR_EXPRESSION, BUILT_IN_TYPE_EXPRESSION, CAST_EXPRESSION,
      CLOSURE, CONSTRUCTOR_CALL_EXPRESSION, ELVIS_EXPRESSION, EQUALITY_EXPRESSION,
      EXPRESSION, GSTRING, INDEX_EXPRESSION, INSTANCEOF_EXPRESSION,
      IN_EXPRESSION, LAND_EXPRESSION, LIST_OR_MAP, LITERAL,
      LOR_EXPRESSION, METHOD_CALL_EXPRESSION, METHOD_REFERENCE_EXPRESSION, MULTIPLICATIVE_EXPRESSION,
      NEW_EXPRESSION, PARENTHESIZED_EXPRESSION, POWER_EXPRESSION, PROPERTY_EXPRESSION,
      RANGE_EXPRESSION, REFERENCE_EXPRESSION, REGEX, REGEX_FIND_EXPRESSION,
      REGEX_MATCH_EXPRESSION, RELATIONAL_EXPRESSION, SHIFT_EXPRESSION, TERNARY_EXPRESSION,
      TUPLE_ASSIGNMENT_EXPRESSION, UNARY_EXPRESSION, XOR_EXPRESSION),
  };

  /* ********************************************************** */
  // <<a>> (<<b>> <<a>>)*
  static boolean a_b_a(PsiBuilder b, int l, Parser _a, Parser _b) {
    if (!recursion_guard_(b, l, "a_b_a")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = _a.parse(b, l);
    p = r; // pin = 1
    r = r && a_b_a_1(b, l + 1, _b, _a);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (<<b>> <<a>>)*
  private static boolean a_b_a_1(PsiBuilder b, int l, Parser _b, Parser _a) {
    if (!recursion_guard_(b, l, "a_b_a_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!a_b_a_1_0(b, l + 1, _b, _a)) break;
      if (!empty_element_parsed_guard_(b, "a_b_a_1", c)) break;
    }
    return true;
  }

  // <<b>> <<a>>
  private static boolean a_b_a_1_0(PsiBuilder b, int l, Parser _b, Parser _a) {
    if (!recursion_guard_(b, l, "a_b_a_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _b.parse(b, l);
    r = r && _a.parse(b, l);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<a>> (<<b>> <<a>>)*
  static boolean a_b_a_p(PsiBuilder b, int l, Parser _a, Parser _b) {
    if (!recursion_guard_(b, l, "a_b_a_p")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = _a.parse(b, l);
    p = r; // pin = 1
    r = r && a_b_a_p_1(b, l + 1, _b, _a);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (<<b>> <<a>>)*
  private static boolean a_b_a_p_1(PsiBuilder b, int l, Parser _b, Parser _a) {
    if (!recursion_guard_(b, l, "a_b_a_p_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!a_b_a_p_1_0(b, l + 1, _b, _a)) break;
      if (!empty_element_parsed_guard_(b, "a_b_a_p_1", c)) break;
    }
    return true;
  }

  // <<b>> <<a>>
  private static boolean a_b_a_p_1_0(PsiBuilder b, int l, Parser _b, Parser _a) {
    if (!recursion_guard_(b, l, "a_b_a_p_1_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = _b.parse(b, l);
    p = r; // pin = 1
    r = r && _a.parse(b, l);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // mb_nl annotation_argument_list | empty_annotation_argument_list
  static boolean after_annotation_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "after_annotation_reference")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = after_annotation_reference_0(b, l + 1);
    if (!r) r = empty_annotation_argument_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // mb_nl annotation_argument_list
  private static boolean after_annotation_reference_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "after_annotation_reference_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && annotation_argument_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // if_header mb_nl branch [mb_separators else_branch]
  static boolean after_if_keyword(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "after_if_keyword")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = if_header(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && report_error_(b, branch(b, l + 1)) && r;
    r = p && after_if_keyword_3(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // [mb_separators else_branch]
  private static boolean after_if_keyword_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "after_if_keyword_3")) return false;
    after_if_keyword_3_0(b, l + 1);
    return true;
  }

  // mb_separators else_branch
  private static boolean after_if_keyword_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "after_if_keyword_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_separators(b, l + 1);
    r = r && else_branch(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ':' mb_nl block_or_statement
  static boolean after_label(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "after_label")) return false;
    if (!nextTokenIsFast(b, T_COLON)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokenFast(b, T_COLON);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && block_or_statement(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '@' annotation_reference after_annotation_reference
  public static boolean annotation(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation")) return false;
    if (!nextTokenIsFast(b, T_AT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_AT);
    r = r && annotation_reference(b, l + 1);
    r = r && after_annotation_reference(b, l + 1);
    exit_section_(b, m, ANNOTATION, r);
    return r;
  }

  /* ********************************************************** */
  // <<paren_list annotation_member_value_pair>>
  public static boolean annotation_argument_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_argument_list")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = paren_list(b, l + 1, annotation_member_value_pair_parser_);
    exit_section_(b, m, ANNOTATION_ARGUMENT_LIST, r);
    return r;
  }

  /* ********************************************************** */
  // '[' <<comma_list annotation_value>>? ']'
  public static boolean annotation_array_value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_array_value")) return false;
    if (!nextTokenIs(b, T_LBRACK)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ANNOTATION_ARRAY_VALUE, null);
    r = consumeToken(b, T_LBRACK);
    p = r; // pin = 1
    r = r && report_error_(b, annotation_array_value_1(b, l + 1));
    r = p && consumeToken(b, T_RBRACK) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // <<comma_list annotation_value>>?
  private static boolean annotation_array_value_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_array_value_1")) return false;
    comma_list(b, l + 1, annotation_value_parser_);
    return true;
  }

  /* ********************************************************** */
  // '{' mb_nl annotation_members '}'
  public static boolean annotation_body(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_body")) return false;
    if (!nextTokenIs(b, T_LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CLASS_BODY, null);
    r = consumeToken(b, T_LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && report_error_(b, annotation_members(b, l + 1)) && r;
    r = p && consumeToken(b, T_RBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // IDENTIFIER
  static boolean annotation_definition_header(PsiBuilder b, int l) {
    return consumeToken(b, IDENTIFIER);
  }

  /* ********************************************************** */
  // IDENTIFIER | 'as' | 'def' | 'in'
  static boolean annotation_key(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_key")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = consumeToken(b, KW_AS);
    if (!r) r = consumeToken(b, KW_DEF);
    if (!r) r = consumeToken(b, KW_IN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // type_definition | parse_annotation_declaration
  static boolean annotation_level(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_level")) return false;
    boolean r;
    r = type_definition(b, l + 1);
    if (!r) r = parse_annotation_declaration(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // <<class_level_item annotation_level>>
  static boolean annotation_level_item(PsiBuilder b, int l) {
    return class_level_item(b, l + 1, annotation_level_parser_);
  }

  /* ********************************************************** */
  // annotation_key '=' annotation_value | annotation_value
  public static boolean annotation_member_value_pair(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_member_value_pair")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ANNOTATION_MEMBER_VALUE_PAIR, "<annotation attribute>");
    r = annotation_member_value_pair_0(b, l + 1);
    if (!r) r = annotation_value(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // annotation_key '=' annotation_value
  private static boolean annotation_member_value_pair_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_member_value_pair_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = annotation_key(b, l + 1);
    r = r && consumeToken(b, T_ASSIGN);
    p = r; // pin = 2
    r = r && annotation_value(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // annotation_level_item*
  static boolean annotation_members(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_members")) return false;
    while (true) {
      int c = current_position_(b);
      if (!annotation_level_item(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "annotation_members", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // method_identifier parse_parameter_list [mb_nl annotation_method_default] nl_throws
  public static boolean annotation_method(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_method")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ANNOTATION_METHOD, "<annotation method>");
    r = method_identifier(b, l + 1);
    r = r && parse_parameter_list(b, l + 1);
    p = r; // pin = parse_parameter_list
    r = r && report_error_(b, annotation_method_2(b, l + 1));
    r = p && nl_throws(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // [mb_nl annotation_method_default]
  private static boolean annotation_method_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_method_2")) return false;
    annotation_method_2_0(b, l + 1);
    return true;
  }

  // mb_nl annotation_method_default
  private static boolean annotation_method_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_method_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && annotation_method_default(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'default' annotation_value
  static boolean annotation_method_default(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_method_default")) return false;
    if (!nextTokenIs(b, KW_DEFAULT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, KW_DEFAULT);
    p = r; // pin = 1
    r = r && annotation_value(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // <<a_b_a annotation mb_nl>>?
  public static boolean annotation_modifier_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_modifier_list")) return false;
    Marker m = enter_section_(b, l, _NONE_, MODIFIER_LIST, "<annotation modifier list>");
    a_b_a(b, l + 1, annotation_parser_, mb_nl_parser_);
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // simple_reference
  static boolean annotation_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_reference")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<annotation reference>");
    r = simple_reference(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<mb_nl_group (annotation_method | field_declaration)>>
  static boolean annotation_tails(PsiBuilder b, int l) {
    return mb_nl_group(b, l + 1, annotation_tails_0_0_parser_);
  }

  // annotation_method | field_declaration
  private static boolean annotation_tails_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_tails_0_0")) return false;
    boolean r;
    r = annotation_method(b, l + 1);
    if (!r) r = field_declaration(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // '@' ('interface') annotation_definition_header mb_nl annotation_body
  public static boolean annotation_type_definition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_type_definition")) return false;
    if (!nextTokenIsFast(b, T_AT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _LEFT_, ANNOTATION_TYPE_DEFINITION, null);
    r = consumeTokenFast(b, T_AT);
    r = r && annotation_type_definition_1(b, l + 1);
    r = r && annotation_definition_header(b, l + 1);
    p = r; // pin = annotation_definition_header
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && annotation_body(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ('interface')
  private static boolean annotation_type_definition_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_type_definition_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, KW_INTERFACE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // annotation | annotation_array_value | expression
  static boolean annotation_value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "annotation_value")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<annotation attribute initializer>");
    r = annotation(b, l + 1);
    if (!r) r = annotation_array_value(b, l + 1);
    if (!r) r = expression(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // code_reference call_argument_list class_body
  public static boolean anonymous_type_definition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anonymous_type_definition")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = code_reference(b, l + 1);
    r = r && call_argument_list(b, l + 1);
    r = r && class_body(b, l + 1);
    exit_section_(b, m, ANONYMOUS_TYPE_DEFINITION, r);
    return r;
  }

  /* ********************************************************** */
  // <<anyTypeElement type_element>>
  static boolean any_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "any_type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<type>");
    r = anyTypeElement(b, l + 1, type_element_parser_);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // no_nl <<parseApplication application_ref application_expression application_call application_index>>
  static boolean application(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = noNewLine(b, l + 1);
    r = r && parseApplication(b, l + 1, application_ref_parser_, application_expression_parser_, application_call_parser_, application_index_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<applicationArguments application_arguments>>
  public static boolean application_argument_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_argument_list")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, APPLICATION_ARGUMENT_LIST, "<application argument list>");
    r = applicationArguments(b, l + 1, application_arguments_parser_);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  //                                      | simple_literal_tokens
  //                                      | primitive_type
  //                                      | '~' | '!'
  //                                      | 'this' | 'super'
  static boolean application_argument_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_argument_start")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, IDENTIFIER);
    if (!r) r = simple_literal_tokens(b, l + 1);
    if (!r) r = parsePrimitiveType(b, l + 1);
    if (!r) r = consumeTokenFast(b, T_BNOT);
    if (!r) r = consumeTokenFast(b, T_NOT);
    if (!r) r = consumeTokenFast(b, KW_THIS);
    if (!r) r = consumeTokenFast(b, KW_SUPER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // argument (',' mb_nl argument)*
  static boolean application_arguments(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_arguments")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = argument(b, l + 1);
    r = r && application_arguments_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (',' mb_nl argument)*
  private static boolean application_arguments_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_arguments_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!application_arguments_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "application_arguments_1", c)) break;
    }
    return true;
  }

  // ',' mb_nl argument
  private static boolean application_arguments_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_arguments_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_COMMA);
    r = r && mb_nl(b, l + 1);
    r = r && argument(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // call_argument_list lazy_closure* | empty_argument_list lazy_closure+
  public static boolean application_call(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_call")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _LEFT_, METHOD_CALL_EXPRESSION, "<application call>");
    r = application_call_0(b, l + 1);
    if (!r) r = application_call_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // call_argument_list lazy_closure*
  private static boolean application_call_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_call_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = call_argument_list(b, l + 1);
    r = r && application_call_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // lazy_closure*
  private static boolean application_call_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_call_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!lazy_closure(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "application_call_0_1", c)) break;
    }
    return true;
  }

  // empty_argument_list lazy_closure+
  private static boolean application_call_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_call_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = empty_argument_list(b, l + 1);
    r = r && application_call_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // lazy_closure+
  private static boolean application_call_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_call_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = lazy_closure(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!lazy_closure(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "application_call_1_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // application_argument_list | clear_variants_and_fail
  public static boolean application_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_expression")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _LEFT_, APPLICATION_EXPRESSION, "<application expression>");
    r = application_argument_list(b, l + 1);
    if (!r) r = clear_variants_and_fail(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // mb_question index_expression_argument_list
  public static boolean application_index(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_index")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _LEFT_, APPLICATION_INDEX, "<application index>");
    r = mb_question(b, l + 1);
    r = r && index_expression_argument_list(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER | simple_literal_tokens
  public static boolean application_ref(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "application_ref")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _LEFT_, REFERENCE_EXPRESSION, "<application ref>");
    r = consumeTokenFast(b, IDENTIFIER);
    if (!r) r = simple_literal_tokens(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // spread_list_argument | named_argument | <<parseExpressionOrMapArgument expression>>
  static boolean argument(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argument")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = spread_list_argument(b, l + 1);
    if (!r) r = named_argument(b, l + 1);
    if (!r) r = parseExpressionOrMapArgument(b, l + 1, expression_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER | STR_SQ | STR_DQ | '*' | 'null' | 'class' | primitive_type | 'default'
  public static boolean argument_label(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argument_label")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ARGUMENT_LABEL, "<argument label>");
    r = consumeTokenFast(b, IDENTIFIER);
    if (!r) r = consumeTokenFast(b, STR_SQ);
    if (!r) r = consumeTokenFast(b, STR_DQ);
    if (!r) r = consumeTokenFast(b, T_STAR);
    if (!r) r = consumeTokenFast(b, KW_NULL);
    if (!r) r = consumeTokenFast(b, KW_CLASS);
    if (!r) r = parsePrimitiveType(b, l + 1);
    if (!r) r = consumeTokenFast(b, KW_DEFAULT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<argument_list_item_head <<brace>>>> <<argument_list_item_end <<brace>>>>
  static boolean argument_list_item(PsiBuilder b, int l, Parser _brace) {
    if (!recursion_guard_(b, l, "argument_list_item")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = argument_list_item_head(b, l + 1, _brace);
    p = r; // pin = 1
    r = r && argument_list_item_end(b, l + 1, _brace);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // ',' | &<<brace>>
  static boolean argument_list_item_end(PsiBuilder b, int l, Parser _brace) {
    if (!recursion_guard_(b, l, "argument_list_item_end")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_COMMA);
    if (!r) r = argument_list_item_end_1(b, l + 1, _brace);
    exit_section_(b, m, null, r);
    return r;
  }

  // &<<brace>>
  private static boolean argument_list_item_end_1(PsiBuilder b, int l, Parser _brace) {
    if (!recursion_guard_(b, l, "argument_list_item_end_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = _brace.parse(b, l);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<argument_list_item_head0 <<brace>> <<argument_list_item_recovery <<brace>>>>>>
  static boolean argument_list_item_head(PsiBuilder b, int l, Parser _brace) {
    return argument_list_item_head0(b, l + 1, _brace, new Parser() {
      public boolean parse(PsiBuilder b, int l) {
        return argument_list_item_recovery(b, l + 1, _brace);
      }
    });
  }

  /* ********************************************************** */
  // !(end_of_file | <<brace>>) parse_argument
  static boolean argument_list_item_head0(PsiBuilder b, int l, Parser _brace, Parser _recovery) {
    if (!recursion_guard_(b, l, "argument_list_item_head0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = argument_list_item_head0_0(b, l + 1, _brace, _recovery);
    p = r; // pin = 1
    r = r && parse_argument(b, l + 1);
    exit_section_(b, l, m, r, p, _recovery);
    return r || p;
  }

  // !(end_of_file | <<brace>>)
  private static boolean argument_list_item_head0_0(PsiBuilder b, int l, Parser _brace, Parser _recovery) {
    if (!recursion_guard_(b, l, "argument_list_item_head0_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !argument_list_item_head0_0_0(b, l + 1, _brace, _recovery);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // end_of_file | <<brace>>
  private static boolean argument_list_item_head0_0_0(PsiBuilder b, int l, Parser _brace, Parser _recovery) {
    if (!recursion_guard_(b, l, "argument_list_item_head0_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = eof(b, l + 1);
    if (!r) r = _brace.parse(b, l);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // !(<<brace>> | ',' | '}' | qualified_reference_expression_identifiers | expression_start | argument_label)
  static boolean argument_list_item_recovery(PsiBuilder b, int l, Parser _brace) {
    if (!recursion_guard_(b, l, "argument_list_item_recovery")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !argument_list_item_recovery_0(b, l + 1, _brace);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<brace>> | ',' | '}' | qualified_reference_expression_identifiers | expression_start | argument_label
  private static boolean argument_list_item_recovery_0(PsiBuilder b, int l, Parser _brace) {
    if (!recursion_guard_(b, l, "argument_list_item_recovery_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _brace.parse(b, l);
    if (!r) r = consumeToken(b, T_COMMA);
    if (!r) r = consumeToken(b, T_RBRACE);
    if (!r) r = qualified_reference_expression_identifiers(b, l + 1);
    if (!r) r = expression_start(b, l + 1);
    if (!r) r = argument_label(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // mandatory_expression optional_expression*
  public static boolean array_declaration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_declaration")) return false;
    if (!nextTokenIsFast(b, T_LBRACK)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mandatory_expression(b, l + 1);
    r = r && array_declaration_1(b, l + 1);
    exit_section_(b, m, ARRAY_DECLARATION, r);
    return r;
  }

  // optional_expression*
  private static boolean array_declaration_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_declaration_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!optional_expression(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "array_declaration_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '[' mb_nl ']'
  public static boolean array_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_type_element")) return false;
    if (!nextTokenIsFast(b, T_LBRACK)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _LEFT_, ARRAY_TYPE_ELEMENT, "<type>");
    r = consumeTokenFast(b, T_LBRACK);
    r = r && mb_nl(b, l + 1);
    r = r && consumeToken(b, T_RBRACK);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // array_type_element*
  static boolean array_type_elements(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_type_elements")) return false;
    while (true) {
      int c = current_position_(b);
      if (!array_type_element(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "array_type_elements", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // assert_message_separator (mb_nl expression)
  static boolean assert_message(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assert_message")) return false;
    if (!nextTokenIs(b, "", T_COMMA, T_COLON)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = assert_message_separator(b, l + 1);
    p = r; // pin = 1
    r = r && assert_message_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // mb_nl expression
  private static boolean assert_message_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assert_message_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && expression(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ':' | ','
  static boolean assert_message_separator(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assert_message_separator")) return false;
    if (!nextTokenIs(b, "", T_COMMA, T_COLON)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_COLON);
    if (!r) r = consumeToken(b, T_COMMA);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'assert' expression assert_message?
  public static boolean assert_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assert_statement")) return false;
    if (!nextTokenIs(b, KW_ASSERT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ASSERT_STATEMENT, null);
    r = consumeToken(b, KW_ASSERT);
    p = r; // pin = 1
    r = r && report_error_(b, expression(b, l + 1, -1));
    r = p && assert_statement_2(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // assert_message?
  private static boolean assert_statement_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assert_statement_2")) return false;
    assert_message(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // mb_nl ('@')
  static boolean attribute_dot(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attribute_dot")) return false;
    if (!nextTokenIsFast(b, T_AT, NL)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && attribute_dot_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ('@')
  private static boolean attribute_dot_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attribute_dot_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_AT);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // declaration_start_modifiers | block_declaration_start_no_modifiers
  static boolean block_declaration_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_declaration_start")) return false;
    boolean r;
    r = declaration_start_modifiers(b, l + 1);
    if (!r) r = block_declaration_start_no_modifiers(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // empty_modifier_list block_declaration_type_element
  static boolean block_declaration_start_no_modifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_declaration_start_no_modifiers")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = empty_modifier_list(b, l + 1);
    r = r && block_declaration_type_element(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<mb_nl_group (method | variable_declaration_tail)>>
  static boolean block_declaration_tail(PsiBuilder b, int l) {
    return mb_nl_group(b, l + 1, block_declaration_tail_0_0_parser_);
  }

  // method | variable_declaration_tail
  private static boolean block_declaration_tail_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_declaration_tail_0_0")) return false;
    boolean r;
    r = method(b, l + 1);
    if (!r) r = variable_declaration_tail(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // (capital_type_element | clear_variants_and_fail) &declaration_lookahead
  static boolean block_declaration_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_declaration_type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<type>");
    r = block_declaration_type_element_0(b, l + 1);
    r = r && block_declaration_type_element_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // capital_type_element | clear_variants_and_fail
  private static boolean block_declaration_type_element_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_declaration_type_element_0")) return false;
    boolean r;
    r = capital_type_element(b, l + 1);
    if (!r) r = clear_variants_and_fail(b, l + 1);
    return r;
  }

  // &declaration_lookahead
  private static boolean block_declaration_type_element_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_declaration_type_element_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = declaration_lookahead(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // top_level_end | &'}'
  static boolean block_level_end(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_level_end")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = top_level_end(b, l + 1);
    if (!r) r = block_level_end_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &'}'
  private static boolean block_level_end_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_level_end_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, T_RBRACE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // extended_statement_item
  //                            | <<addVariant "statement">> <<separated_item block_level_end statement block_level_start>>
  static boolean block_level_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_level_item")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = extended_statement_item(b, l + 1);
    if (!r) r = block_level_item_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<addVariant "statement">> <<separated_item block_level_end statement block_level_start>>
  private static boolean block_level_item_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_level_item_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = addVariant(b, l + 1, "statement");
    r = r && separated_item(b, l + 1, block_level_end_parser_, statement_parser_, block_level_start_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // statement_start | class_level_start
  static boolean block_level_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_level_start")) return false;
    boolean r;
    r = statement_start(b, l + 1);
    if (!r) r = class_level_start(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // block_level_item* clear_error
  public static boolean block_levels(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_levels")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = block_levels_0(b, l + 1);
    r = r && clearError(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // block_level_item*
  private static boolean block_levels_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_levels_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!block_level_item(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "block_levels_0", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // block_statement | statement
  static boolean block_or_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_or_statement")) return false;
    boolean r;
    r = block_statement(b, l + 1);
    if (!r) r = statement(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // lazy_block
  public static boolean block_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_statement")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BLOCK_STATEMENT, "<block statement>");
    r = lazy_block(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // bracket_argument_list_item*
  static boolean bracket_argument_list_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bracket_argument_list_inner")) return false;
    while (true) {
      int c = current_position_(b);
      if (!bracket_argument_list_item(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "bracket_argument_list_inner", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // <<argument_list_item ']'>>
  static boolean bracket_argument_list_item(PsiBuilder b, int l) {
    return argument_list_item(b, l + 1, T_RBRACK_parser_);
  }

  /* ********************************************************** */
  // <<extendedStatement>> | block_or_statement
  static boolean branch(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "branch")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = extendedStatement(b, l + 1);
    if (!r) r = block_or_statement(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'break' IDENTIFIER?
  public static boolean break_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "break_statement")) return false;
    if (!nextTokenIs(b, KW_BREAK)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_BREAK);
    r = r && break_statement_1(b, l + 1);
    exit_section_(b, m, BREAK_STATEMENT, r);
    return r;
  }

  // IDENTIFIER?
  private static boolean break_statement_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "break_statement_1")) return false;
    consumeToken(b, IDENTIFIER);
    return true;
  }

  /* ********************************************************** */
  // '(' (')') | '(' paren_argument_list_inner ')'
  public static boolean call_argument_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "call_argument_list")) return false;
    if (!nextTokenIsFast(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = call_argument_list_0(b, l + 1);
    if (!r) r = call_argument_list_1(b, l + 1);
    exit_section_(b, m, ARGUMENT_LIST, r);
    return r;
  }

  // '(' (')')
  private static boolean call_argument_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "call_argument_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_LPAREN);
    r = r && call_argument_list_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (')')
  private static boolean call_argument_list_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "call_argument_list_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // '(' paren_argument_list_inner ')'
  private static boolean call_argument_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "call_argument_list_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokenFast(b, T_LPAREN);
    p = r; // pin = 1
    r = r && report_error_(b, paren_argument_list_inner(b, l + 1));
    r = p && consumeToken(b, T_RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // &'('
  static boolean call_argument_list_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "call_argument_list_start")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, T_LPAREN);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // call_argument_list (mb_nl lazy_closure)* | empty_argument_list <<closureArgumentSeparator lazy_closure+>>
  static boolean call_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "call_tail")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = call_tail_0(b, l + 1);
    if (!r) r = call_tail_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // call_argument_list (mb_nl lazy_closure)*
  private static boolean call_tail_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "call_tail_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = call_argument_list(b, l + 1);
    r = r && call_tail_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (mb_nl lazy_closure)*
  private static boolean call_tail_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "call_tail_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!call_tail_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "call_tail_0_1", c)) break;
    }
    return true;
  }

  // mb_nl lazy_closure
  private static boolean call_tail_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "call_tail_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && lazy_closure(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // empty_argument_list <<closureArgumentSeparator lazy_closure+>>
  private static boolean call_tail_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "call_tail_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = empty_argument_list(b, l + 1);
    r = r && closureArgumentSeparator(b, l + 1, call_tail_1_1_0_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  // lazy_closure+
  private static boolean call_tail_1_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "call_tail_1_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = lazy_closure(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!lazy_closure(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "call_tail_1_1_0", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<capitalizedTypeElement class_type_element <<refWasCapitalized>>>>
  static boolean capital_class_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "capital_class_type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<type>");
    r = capitalizedTypeElement(b, l + 1, class_type_element_parser_, capital_class_type_element_0_1_parser_);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // (primitive_type_element | capital_class_type_element) array_type_elements
  public static boolean capital_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "capital_type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, TYPE_ELEMENT, "<type>");
    r = capital_type_element_0(b, l + 1);
    r = r && array_type_elements(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // primitive_type_element | capital_class_type_element
  private static boolean capital_type_element_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "capital_type_element_0")) return false;
    boolean r;
    r = primitive_type_element(b, l + 1);
    if (!r) r = capital_class_type_element(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // 'case' expression ':'
  public static boolean case_label(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_label")) return false;
    if (!nextTokenIs(b, KW_CASE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CASE_LABEL, null);
    r = consumeToken(b, KW_CASE);
    p = r; // pin = 1
    r = r && report_error_(b, expression(b, l + 1, -1));
    r = p && consumeToken(b, T_COLON) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // block_level_end | &('case' | 'default')
  static boolean case_level_end(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_level_end")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = block_level_end(b, l + 1);
    if (!r) r = case_level_end_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &('case' | 'default')
  private static boolean case_level_end_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_level_end_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = case_level_end_1_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // 'case' | 'default'
  private static boolean case_level_end_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_level_end_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, KW_CASE);
    if (!r) r = consumeTokenFast(b, KW_DEFAULT);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // extended_statement_item
  //                           | <<addVariant "statement">> <<separated_item case_level_end statement case_level_start>>
  static boolean case_level_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_level_item")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = extended_statement_item(b, l + 1);
    if (!r) r = case_level_item_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<addVariant "statement">> <<separated_item case_level_end statement case_level_start>>
  private static boolean case_level_item_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_level_item_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = addVariant(b, l + 1, "statement");
    r = r && separated_item(b, l + 1, case_level_end_parser_, statement_parser_, case_level_start_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // statement_start | class_level_start | 'case' | 'default'
  static boolean case_level_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_level_start")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = statement_start(b, l + 1);
    if (!r) r = class_level_start(b, l + 1);
    if (!r) r = consumeToken(b, KW_CASE);
    if (!r) r = consumeToken(b, KW_DEFAULT);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // case_label (mb_nl case_label)* (mb_nl case_level_item*)
  public static boolean case_section(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_section")) return false;
    if (!nextTokenIs(b, KW_CASE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CASE_SECTION, null);
    r = case_label(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, case_section_1(b, l + 1));
    r = p && case_section_2(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (mb_nl case_label)*
  private static boolean case_section_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_section_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!case_section_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "case_section_1", c)) break;
    }
    return true;
  }

  // mb_nl case_label
  private static boolean case_section_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_section_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && case_label(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // mb_nl case_level_item*
  private static boolean case_section_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_section_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && case_section_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // case_level_item*
  private static boolean case_section_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_section_2_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!case_level_item(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "case_section_2_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // !('}' | 'case' | 'default')
  static boolean case_section_recovery(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_section_recovery")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !case_section_recovery_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '}' | 'case' | 'default'
  private static boolean case_section_recovery_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_section_recovery_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_RBRACE);
    if (!r) r = consumeToken(b, KW_CASE);
    if (!r) r = consumeToken(b, KW_DEFAULT);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // !'}' (case_section | default_case_section)
  static boolean case_sections(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_sections")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = case_sections_0(b, l + 1);
    p = r; // pin = 1
    r = r && case_sections_1(b, l + 1);
    exit_section_(b, l, m, r, p, case_section_recovery_parser_);
    return r || p;
  }

  // !'}'
  private static boolean case_sections_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_sections_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, T_RBRACE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // case_section | default_case_section
  private static boolean case_sections_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_sections_1")) return false;
    boolean r;
    r = case_section(b, l + 1);
    if (!r) r = default_case_section(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // '(' (IDENTIFIER | primitive_type)
  static boolean cast_expression_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "cast_expression_start")) return false;
    if (!nextTokenIsFast(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_LPAREN);
    r = r && cast_expression_start_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER | primitive_type
  private static boolean cast_expression_start_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "cast_expression_start_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, IDENTIFIER);
    if (!r) r = parsePrimitiveType(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'catch' '(' parse_catch_parameter ')' lazy_block
  public static boolean catch_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "catch_clause")) return false;
    if (!nextTokenIs(b, KW_CATCH)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CATCH_CLAUSE, null);
    r = consumeTokens(b, 1, KW_CATCH, T_LPAREN);
    p = r; // pin = 1
    r = r && report_error_(b, parse_catch_parameter(b, l + 1));
    r = p && report_error_(b, consumeToken(b, T_RPAREN)) && r;
    r = p && lazy_block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean catch_parameter(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "catch_parameter")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, PARAMETER, r);
    return r;
  }

  /* ********************************************************** */
  // modifier_list catch_parameter_type_element?
  static boolean catch_parameter_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "catch_parameter_start")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = modifier_list(b, l + 1);
    r = r && catch_parameter_start_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // catch_parameter_type_element?
  private static boolean catch_parameter_start_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "catch_parameter_start_1")) return false;
    catch_parameter_type_element(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // type_element_followed_by_identifier
  //                                        | type_element disjunction_type_element
  static boolean catch_parameter_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "catch_parameter_type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<type>");
    r = type_element_followed_by_identifier(b, l + 1);
    if (!r) r = catch_parameter_type_element_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // type_element disjunction_type_element
  private static boolean catch_parameter_type_element_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "catch_parameter_type_element_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_element(b, l + 1);
    r = r && disjunction_type_element(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '{' class_body_inner '}'
  public static boolean class_body(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_body")) return false;
    if (!nextTokenIs(b, T_LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CLASS_BODY, null);
    r = consumeToken(b, T_LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, class_body_inner(b, l + 1));
    r = p && consumeToken(b, T_RBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // mb_separators class_members
  public static boolean class_body_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_body_inner")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_separators(b, l + 1);
    r = r && class_members(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // declaration_start_modifiers | class_declaration_start_no_modifiers
  static boolean class_declaration_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_declaration_start")) return false;
    boolean r;
    r = declaration_start_modifiers(b, l + 1);
    if (!r) r = class_declaration_start_no_modifiers(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // class_declaration_start_after_no_modifiers1
  //                                                      | class_declaration_start_after_no_modifiers2
  static boolean class_declaration_start_after_no_modifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_declaration_start_after_no_modifiers")) return false;
    boolean r;
    r = class_declaration_start_after_no_modifiers1(b, l + 1);
    if (!r) r = class_declaration_start_after_no_modifiers2(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // capital_type_element !'('
  static boolean class_declaration_start_after_no_modifiers1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_declaration_start_after_no_modifiers1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = capital_type_element(b, l + 1);
    r = r && class_declaration_start_after_no_modifiers1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // !'('
  private static boolean class_declaration_start_after_no_modifiers1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_declaration_start_after_no_modifiers1_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, T_LPAREN);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // declaration_type_element
  static boolean class_declaration_start_after_no_modifiers2(PsiBuilder b, int l) {
    return declaration_type_element(b, l + 1);
  }

  /* ********************************************************** */
  // empty_modifier_list class_declaration_start_after_no_modifiers
  static boolean class_declaration_start_no_modifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_declaration_start_no_modifiers")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = empty_modifier_list(b, l + 1);
    r = r && class_declaration_start_after_no_modifiers(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<mb_nl_group (methods_tail | field_declaration)>>
  static boolean class_declaration_tail(PsiBuilder b, int l) {
    return mb_nl_group(b, l + 1, class_declaration_tail_0_0_parser_);
  }

  // methods_tail | field_declaration
  private static boolean class_declaration_tail_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_declaration_tail_0_0")) return false;
    boolean r;
    r = methods_tail(b, l + 1);
    if (!r) r = field_declaration(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // <<classIdentifier>> type_parameter_list? nl_extends nl_implements
  static boolean class_definition_header(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_definition_header")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = classIdentifier(b, l + 1);
    r = r && class_definition_header_1(b, l + 1);
    r = r && nl_extends(b, l + 1);
    r = r && nl_implements(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_parameter_list?
  private static boolean class_definition_header_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_definition_header_1")) return false;
    type_parameter_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // class_initializer_modifier_list mb_nl lazy_block
  public static boolean class_initializer(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_initializer")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CLASS_INITIALIZER, "<class initializer>");
    r = class_initializer_modifier_list(b, l + 1);
    r = r && mb_nl(b, l + 1);
    r = r && lazy_block(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'static' | !nl
  public static boolean class_initializer_modifier_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_initializer_modifier_list")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MODIFIER_LIST, "<class initializer modifier list>");
    r = consumeTokenFast(b, KW_STATIC);
    if (!r) r = class_initializer_modifier_list_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // !nl
  private static boolean class_initializer_modifier_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_initializer_modifier_list_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !nl(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // class_initializer
  //                       | type_definition
  //                       | tuple_var_declaration <<error "tuple.cant.be.placed.in.class">>
  //                       | parse_class_declaration
  static boolean class_level(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_level")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = class_initializer(b, l + 1);
    if (!r) r = type_definition(b, l + 1);
    if (!r) r = class_level_2(b, l + 1);
    if (!r) r = parse_class_declaration(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // tuple_var_declaration <<error "tuple.cant.be.placed.in.class">>
  private static boolean class_level_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_level_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = tuple_var_declaration(b, l + 1);
    r = r && error(b, l + 1, "tuple.cant.be.placed.in.class");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // separators | <<eof>> | &'}'
  static boolean class_level_end(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_level_end")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = separators(b, l + 1);
    if (!r) r = eof(b, l + 1);
    if (!r) r = class_level_end_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &'}'
  private static boolean class_level_end_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_level_end_2")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, T_RBRACE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<separated_item class_level_end <<item>> class_level_start>>
  static boolean class_level_item(PsiBuilder b, int l, Parser _item) {
    return separated_item(b, l + 1, class_level_end_parser_, _item, class_level_start_parser_);
  }

  /* ********************************************************** */
  // IDENTIFIER | modifier | '{' | '@' | 'class' | 'interface' | 'trait' | 'enum' | primitive_type
  static boolean class_level_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_level_start")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = modifier(b, l + 1);
    if (!r) r = consumeToken(b, T_LBRACE);
    if (!r) r = consumeToken(b, T_AT);
    if (!r) r = consumeToken(b, KW_CLASS);
    if (!r) r = consumeToken(b, KW_INTERFACE);
    if (!r) r = consumeToken(b, KW_TRAIT);
    if (!r) r = consumeToken(b, KW_ENUM);
    if (!r) r = parsePrimitiveType(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<class_level_item class_level>>
  static boolean class_member(PsiBuilder b, int l) {
    return class_level_item(b, l + 1, class_level_parser_);
  }

  /* ********************************************************** */
  // class_member* clear_error
  static boolean class_members(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_members")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = class_members_0(b, l + 1);
    r = r && clearError(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // class_member*
  private static boolean class_members_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_members_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!class_member(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "class_members_0", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // 'class' class_definition_header mb_nl class_body <<popClassIdentifier>>
  public static boolean class_type_definition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_type_definition")) return false;
    if (!nextTokenIsFast(b, KW_CLASS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _LEFT_, CLASS_TYPE_DEFINITION, null);
    r = consumeTokenFast(b, KW_CLASS);
    r = r && class_definition_header(b, l + 1);
    p = r; // pin = class_definition_header
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && report_error_(b, class_body(b, l + 1)) && r;
    r = p && popClassIdentifier(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // code_reference
  public static boolean class_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "class_type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CLASS_TYPE_ELEMENT, "<type>");
    r = code_reference(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // clear_variants fail
  static boolean clear_variants_and_fail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "clear_variants_and_fail")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = clearVariants(b, l + 1);
    r = r && noMatch(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '{' mb_nl closure_header mb_separators block_levels '}'
  public static boolean closure(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "closure")) return false;
    if (!nextTokenIsFast(b, T_LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CLOSURE, null);
    r = consumeTokenFast(b, T_LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && report_error_(b, closure_header(b, l + 1)) && r;
    r = p && report_error_(b, mb_separators(b, l + 1)) && r;
    r = p && report_error_(b, block_levels(b, l + 1)) && r;
    r = p && consumeToken(b, T_RBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '->'
  static boolean closure_arrow(PsiBuilder b, int l) {
    return consumeToken(b, T_ARROW);
  }

  /* ********************************************************** */
  // empty_parameter_list closure_arrow | closure_parameter_list mb_nl closure_arrow | empty_parameter_list
  static boolean closure_header(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "closure_header")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = closure_header_0(b, l + 1);
    if (!r) r = closure_header_1(b, l + 1);
    if (!r) r = empty_parameter_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // empty_parameter_list closure_arrow
  private static boolean closure_header_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "closure_header_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = empty_parameter_list(b, l + 1);
    r = r && closure_arrow(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // closure_parameter_list mb_nl closure_arrow
  private static boolean closure_header_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "closure_header_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = closure_parameter_list(b, l + 1);
    r = r && mb_nl(b, l + 1);
    r = r && closure_arrow(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<comma_list (mb_nl parse_parameter)>>
  public static boolean closure_parameter_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "closure_parameter_list")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, PARAMETER_LIST, "<closure parameter list>");
    r = comma_list(b, l + 1, closure_parameter_list_0_0_parser_);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // mb_nl parse_parameter
  private static boolean closure_parameter_list_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "closure_parameter_list_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && parse_parameter(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // short_code_reference code_reference_tail*
  public static boolean code_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, CODE_REFERENCE, "<code reference>");
    r = short_code_reference(b, l + 1);
    r = r && code_reference_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // code_reference_tail*
  private static boolean code_reference_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!code_reference_tail(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "code_reference_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '.' !'*' mb_nl
  static boolean code_reference_dot(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference_dot")) return false;
    if (!nextTokenIs(b, T_DOT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_DOT);
    r = r && code_reference_dot_1(b, l + 1);
    r = r && mb_nl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // !'*'
  private static boolean code_reference_dot_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference_dot_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, T_STAR);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<codeReferenceIdentifier code_reference_identifiers>>
  static boolean code_reference_identifier(PsiBuilder b, int l) {
    return codeReferenceIdentifier(b, l + 1, code_reference_identifiers_parser_);
  }

  /* ********************************************************** */
  // IDENTIFIER | code_reference_identifiers_soft (<<isQualifiedName>> | &'.')
  static boolean code_reference_identifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference_identifiers")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = code_reference_identifiers_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // code_reference_identifiers_soft (<<isQualifiedName>> | &'.')
  private static boolean code_reference_identifiers_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference_identifiers_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = code_reference_identifiers_soft(b, l + 1);
    r = r && code_reference_identifiers_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<isQualifiedName>> | &'.'
  private static boolean code_reference_identifiers_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference_identifiers_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = isQualifiedName(b, l + 1);
    if (!r) r = code_reference_identifiers_1_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &'.'
  private static boolean code_reference_identifiers_1_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference_identifiers_1_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, T_DOT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'def' | 'as' | 'in' | 'trait'
  static boolean code_reference_identifiers_soft(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference_identifiers_soft")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, KW_DEF);
    if (!r) r = consumeTokenFast(b, KW_AS);
    if (!r) r = consumeTokenFast(b, KW_IN);
    if (!r) r = consumeTokenFast(b, KW_TRAIT);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // code_reference_identifier [type_argument_list <<setRefHadTypeArguments>>]
  static boolean code_reference_part(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference_part")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = code_reference_identifier(b, l + 1);
    r = r && code_reference_part_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [type_argument_list <<setRefHadTypeArguments>>]
  private static boolean code_reference_part_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference_part_1")) return false;
    code_reference_part_1_0(b, l + 1);
    return true;
  }

  // type_argument_list <<setRefHadTypeArguments>>
  private static boolean code_reference_part_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference_part_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_argument_list(b, l + 1);
    r = r && setRefHadTypeArguments(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // code_reference_dot code_reference_part <<setRefWasQualified>>
  public static boolean code_reference_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "code_reference_tail")) return false;
    if (!nextTokenIsFast(b, T_DOT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _LEFT_, CODE_REFERENCE, null);
    r = code_reference_dot(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, code_reference_part(b, l + 1));
    r = p && setRefWasQualified(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // <<a_b_a <<item>> fast_comma>>
  static boolean comma_list(PsiBuilder b, int l, Parser _item) {
    return a_b_a(b, l + 1, _item, fast_comma_parser_);
  }

  /* ********************************************************** */
  // <<a_b_a_p <<item>> fast_comma>>
  static boolean comma_list_p(PsiBuilder b, int l, Parser _item) {
    return a_b_a_p(b, l + 1, _item, fast_comma_parser_);
  }

  /* ********************************************************** */
  // <<begin>> (<<string_content <<content>>>> | string_injection)* <<end>>
  static boolean compound_string(PsiBuilder b, int l, Parser _begin, Parser _content, Parser _end) {
    if (!recursion_guard_(b, l, "compound_string")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = _begin.parse(b, l);
    p = r; // pin = 1
    r = r && report_error_(b, compound_string_1(b, l + 1, _content));
    r = p && _end.parse(b, l) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (<<string_content <<content>>>> | string_injection)*
  private static boolean compound_string_1(PsiBuilder b, int l, Parser _content) {
    if (!recursion_guard_(b, l, "compound_string_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!compound_string_1_0(b, l + 1, _content)) break;
      if (!empty_element_parsed_guard_(b, "compound_string_1", c)) break;
    }
    return true;
  }

  // <<string_content <<content>>>> | string_injection
  private static boolean compound_string_1_0(PsiBuilder b, int l, Parser _content) {
    if (!recursion_guard_(b, l, "compound_string_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = string_content(b, l + 1, _content);
    if (!r) r = string_injection(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // constructor_identifier parse_parameter_list nl_throws [mb_nl lazy_constructor_block]
  public static boolean constructor(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CONSTRUCTOR, "<constructor>");
    r = constructor_identifier(b, l + 1);
    r = r && parse_parameter_list(b, l + 1);
    p = r; // pin = parse_parameter_list
    r = r && report_error_(b, nl_throws(b, l + 1));
    r = p && constructor_3(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // [mb_nl lazy_constructor_block]
  private static boolean constructor_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_3")) return false;
    constructor_3_0(b, l + 1);
    return true;
  }

  // mb_nl lazy_constructor_block
  private static boolean constructor_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && lazy_constructor_block(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '{' mb_separators [constructor_call_expression block_level_end] block_levels '}'
  public static boolean constructor_block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_block")) return false;
    if (!nextTokenIs(b, T_LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CONSTRUCTOR_BLOCK, null);
    r = consumeToken(b, T_LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, mb_separators(b, l + 1));
    r = p && report_error_(b, constructor_block_2(b, l + 1)) && r;
    r = p && report_error_(b, block_levels(b, l + 1)) && r;
    r = p && consumeToken(b, T_RBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // [constructor_call_expression block_level_end]
  private static boolean constructor_block_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_block_2")) return false;
    constructor_block_2_0(b, l + 1);
    return true;
  }

  // constructor_call_expression block_level_end
  private static boolean constructor_block_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_block_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = constructor_call_expression(b, l + 1);
    r = r && block_level_end(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // &('this' | 'super') unqualified_reference_expression call_tail
  public static boolean constructor_call_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_call_expression")) return false;
    if (!nextTokenIsFast(b, KW_SUPER, KW_THIS)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CONSTRUCTOR_CALL_EXPRESSION, "<constructor call expression>");
    r = constructor_call_expression_0(b, l + 1);
    r = r && unqualified_reference_expression(b, l + 1);
    r = r && call_tail(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // &('this' | 'super')
  private static boolean constructor_call_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_call_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = constructor_call_expression_0_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // 'this' | 'super'
  private static boolean constructor_call_expression_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_call_expression_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, KW_THIS);
    if (!r) r = consumeTokenFast(b, KW_SUPER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<constructorIdentifier>>
  static boolean constructor_identifier(PsiBuilder b, int l) {
    return constructorIdentifier(b, l + 1);
  }

  /* ********************************************************** */
  // 'continue' IDENTIFIER?
  public static boolean continue_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "continue_statement")) return false;
    if (!nextTokenIs(b, KW_CONTINUE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_CONTINUE);
    r = r && continue_statement_1(b, l + 1);
    exit_section_(b, m, CONTINUE_STATEMENT, r);
    return r;
  }

  // IDENTIFIER?
  private static boolean continue_statement_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "continue_statement_1")) return false;
    consumeToken(b, IDENTIFIER);
    return true;
  }

  /* ********************************************************** */
  // method_lookahead | IDENTIFIER
  static boolean declaration_lookahead(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "declaration_lookahead")) return false;
    boolean r;
    r = method_lookahead(b, l + 1);
    if (!r) r = consumeTokenFast(b, IDENTIFIER);
    return r;
  }

  /* ********************************************************** */
  // non_empty_modifier_list mb_type_parameter_list mb_declaration_type_element
  static boolean declaration_start_modifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "declaration_start_modifiers")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = non_empty_modifier_list(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, mb_type_parameter_list(b, l + 1));
    r = p && mb_declaration_type_element(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // definitely_type_element | clear_variants_and_fail
  static boolean declaration_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "declaration_type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<type>");
    r = definitely_type_element(b, l + 1);
    if (!r) r = clear_variants_and_fail(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'def' !'.'
  static boolean def_modifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "def_modifier")) return false;
    if (!nextTokenIsFast(b, KW_DEF)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, KW_DEF);
    r = r && def_modifier_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // !'.'
  private static boolean def_modifier_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "def_modifier_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeTokenFast(b, T_DOT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // default_label mb_nl case_level_item+
  public static boolean default_case_section(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "default_case_section")) return false;
    if (!nextTokenIs(b, KW_DEFAULT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CASE_SECTION, null);
    r = default_label(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && default_case_section_2(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // case_level_item+
  private static boolean default_case_section_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "default_case_section_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = case_level_item(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!case_level_item(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "default_case_section_2", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'default' ':'
  public static boolean default_label(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "default_label")) return false;
    if (!nextTokenIs(b, KW_DEFAULT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CASE_LABEL, null);
    r = consumeTokens(b, 1, KW_DEFAULT, T_COLON);
    p = r; // pin = 1
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // <<definitelyTypeElement type_element (<<wasDefinitelyTypeElement>> | &declaration_lookahead)>>
  static boolean definitely_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "definitely_type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<type>");
    r = definitelyTypeElement(b, l + 1, type_element_parser_, definitely_type_element_0_1_parser_);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<wasDefinitelyTypeElement>> | &declaration_lookahead
  private static boolean definitely_type_element_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "definitely_type_element_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = wasDefinitelyTypeElement(b, l + 1);
    if (!r) r = definitely_type_element_0_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &declaration_lookahead
  private static boolean definitely_type_element_0_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "definitely_type_element_0_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = declaration_lookahead(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<isDiamondAllowed>> fast_l_angle '>'
  static boolean diamond_type_argument_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diamond_type_argument_list")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = isDiamondAllowed(b, l + 1);
    r = r && fast_l_angle(b, l + 1);
    r = r && consumeToken(b, T_GT);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // disjunction_type_element_part+
  public static boolean disjunction_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "disjunction_type_element")) return false;
    if (!nextTokenIs(b, "<type>", T_BOR)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _LEFT_, DISJUNCTION_TYPE_ELEMENT, "<type>");
    r = disjunction_type_element_part(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!disjunction_type_element_part(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "disjunction_type_element", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '|' (type_element | expect_type)
  static boolean disjunction_type_element_part(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "disjunction_type_element_part")) return false;
    if (!nextTokenIsFast(b, T_BOR)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokenFast(b, T_BOR);
    p = r; // pin = 1
    r = r && disjunction_type_element_part_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // type_element | expect_type
  private static boolean disjunction_type_element_part_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "disjunction_type_element_part_1")) return false;
    boolean r;
    r = type_element(b, l + 1);
    if (!r) r = expect_type(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // 'do' mb_nl branch mb_nl 'while' '(' expression ')'
  public static boolean do_while_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "do_while_statement")) return false;
    if (!nextTokenIs(b, KW_DO)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, DO_WHILE_STATEMENT, null);
    r = consumeToken(b, KW_DO);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && report_error_(b, branch(b, l + 1)) && r;
    r = p && report_error_(b, mb_nl(b, l + 1)) && r;
    r = p && report_error_(b, consumeTokens(b, -1, KW_WHILE, T_LPAREN)) && r;
    r = p && report_error_(b, expression(b, l + 1, -1)) && r;
    r = p && consumeToken(b, T_RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // DOLLAR_SLASHY_BEGIN fast_dollar_slashy_content? !'$' DOLLAR_SLASHY_END
  public static boolean dollar_slashy_literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dollar_slashy_literal")) return false;
    if (!nextTokenIs(b, DOLLAR_SLASHY_BEGIN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, DOLLAR_SLASHY_LITERAL, null);
    r = consumeToken(b, DOLLAR_SLASHY_BEGIN);
    r = r && dollar_slashy_literal_1(b, l + 1);
    r = r && dollar_slashy_literal_2(b, l + 1);
    p = r; // pin = 3
    r = r && consumeToken(b, DOLLAR_SLASHY_END);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // fast_dollar_slashy_content?
  private static boolean dollar_slashy_literal_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dollar_slashy_literal_1")) return false;
    fast_dollar_slashy_content(b, l + 1);
    return true;
  }

  // !'$'
  private static boolean dollar_slashy_literal_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dollar_slashy_literal_2")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, T_DOLLAR);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<compound_string DOLLAR_SLASHY_BEGIN fast_dollar_slashy_content DOLLAR_SLASHY_END>>
  static boolean dollar_slashy_string(PsiBuilder b, int l) {
    return compound_string(b, l + 1, DOLLAR_SLASHY_BEGIN_parser_, fast_dollar_slashy_content_parser_, DOLLAR_SLASHY_END_parser_);
  }

  /* ********************************************************** */
  // '...'
  static boolean ellipsis(PsiBuilder b, int l) {
    return consumeTokenFast(b, T_ELLIPSIS);
  }

  /* ********************************************************** */
  // 'else' mb_nl branch
  static boolean else_branch(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "else_branch")) return false;
    if (!nextTokenIs(b, KW_ELSE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, KW_ELSE);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && branch(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // ()
  static boolean empty(PsiBuilder b, int l) {
    return true;
  }

  /* ********************************************************** */
  public static boolean empty_annotation_argument_list(PsiBuilder b, int l) {
    Marker m = enter_section_(b);
    exit_section_(b, m, ANNOTATION_ARGUMENT_LIST, true);
    return true;
  }

  /* ********************************************************** */
  public static boolean empty_argument_list(PsiBuilder b, int l) {
    Marker m = enter_section_(b);
    exit_section_(b, m, ARGUMENT_LIST, true);
    return true;
  }

  /* ********************************************************** */
  // empty
  public static boolean empty_extends_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "empty_extends_clause")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, EXTENDS_CLAUSE, "<empty extends clause>");
    r = empty(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // empty
  public static boolean empty_implements_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "empty_implements_clause")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, IMPLEMENTS_CLAUSE, "<empty implements clause>");
    r = empty(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '[' mb_nl ']'
  static boolean empty_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "empty_list")) return false;
    if (!nextTokenIsFast(b, T_LBRACK)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_LBRACK);
    r = r && mb_nl(b, l + 1);
    r = r && consumeToken(b, T_RBRACK);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '[' mb_nl fast_colon mb_nl ']'
  static boolean empty_map(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "empty_map")) return false;
    if (!nextTokenIsFast(b, T_LBRACK)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_LBRACK);
    r = r && mb_nl(b, l + 1);
    r = r && fast_colon(b, l + 1);
    r = r && mb_nl(b, l + 1);
    r = r && consumeToken(b, T_RBRACK);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  public static boolean empty_modifier_list(PsiBuilder b, int l) {
    Marker m = enter_section_(b);
    exit_section_(b, m, MODIFIER_LIST, true);
    return true;
  }

  /* ********************************************************** */
  public static boolean empty_parameter_list(PsiBuilder b, int l) {
    Marker m = enter_section_(b);
    exit_section_(b, m, PARAMETER_LIST, true);
    return true;
  }

  /* ********************************************************** */
  // empty
  public static boolean empty_throws_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "empty_throws_clause")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, THROWS_CLAUSE, "<empty throws clause>");
    r = empty(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  public static boolean empty_type_parameter_bounds_list(PsiBuilder b, int l) {
    Marker m = enter_section_(b);
    exit_section_(b, m, TYPE_PARAMETER_BOUNDS_LIST, true);
    return true;
  }

  /* ********************************************************** */
  // '{' mb_nl enum_members '}'
  public static boolean enum_body(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_body")) return false;
    if (!nextTokenIs(b, T_LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ENUM_BODY, null);
    r = consumeToken(b, T_LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && report_error_(b, enum_members(b, l + 1)) && r;
    r = p && consumeToken(b, T_RBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // annotation_modifier_list mb_nl IDENTIFIER call_argument_list? enum_constant_initializer?
  public static boolean enum_constant(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_constant")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ENUM_CONSTANT, "<enum constant>");
    r = annotation_modifier_list(b, l + 1);
    r = r && mb_nl(b, l + 1);
    r = r && consumeToken(b, IDENTIFIER);
    p = r; // pin = 3
    r = r && report_error_(b, enum_constant_3(b, l + 1));
    r = p && enum_constant_4(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // call_argument_list?
  private static boolean enum_constant_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_constant_3")) return false;
    call_argument_list(b, l + 1);
    return true;
  }

  // enum_constant_initializer?
  private static boolean enum_constant_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_constant_4")) return false;
    enum_constant_initializer(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // class_body
  public static boolean enum_constant_initializer(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_constant_initializer")) return false;
    if (!nextTokenIs(b, T_LBRACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = class_body(b, l + 1);
    exit_section_(b, m, ENUM_CONSTANT_INITIALIZER, r);
    return r;
  }

  /* ********************************************************** */
  // enum_constant ((mb_nl ',') mb_nl enum_constant)* [mb_nl ',']
  public static boolean enum_constants(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_constants")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ENUM_CONSTANTS, "<enum constants>");
    r = enum_constant(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, enum_constants_1(b, l + 1));
    r = p && enum_constants_2(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ((mb_nl ',') mb_nl enum_constant)*
  private static boolean enum_constants_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_constants_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!enum_constants_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "enum_constants_1", c)) break;
    }
    return true;
  }

  // (mb_nl ',') mb_nl enum_constant
  private static boolean enum_constants_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_constants_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = enum_constants_1_0_0(b, l + 1);
    r = r && mb_nl(b, l + 1);
    r = r && enum_constant(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // mb_nl ','
  private static boolean enum_constants_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_constants_1_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && consumeToken(b, T_COMMA);
    exit_section_(b, m, null, r);
    return r;
  }

  // [mb_nl ',']
  private static boolean enum_constants_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_constants_2")) return false;
    enum_constants_2_0(b, l + 1);
    return true;
  }

  // mb_nl ','
  private static boolean enum_constants_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_constants_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && consumeToken(b, T_COMMA);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // enum_constants class_level_end
  static boolean enum_constants_separated(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_constants_separated")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = enum_constants(b, l + 1);
    p = r; // pin = 1
    r = r && class_level_end(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // <<classIdentifier>> nl_non_empty_extends? nl_implements
  static boolean enum_definition_header(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_definition_header")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = classIdentifier(b, l + 1);
    r = r && enum_definition_header_1(b, l + 1);
    r = r && nl_implements(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // nl_non_empty_extends?
  private static boolean enum_definition_header_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_definition_header_1")) return false;
    nl_non_empty_extends(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // [enum_constants_separated] class_members
  static boolean enum_members(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_members")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = enum_members_0(b, l + 1);
    r = r && class_members(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [enum_constants_separated]
  private static boolean enum_members_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_members_0")) return false;
    enum_constants_separated(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // 'enum' enum_definition_header mb_nl enum_body <<popClassIdentifier>>
  public static boolean enum_type_definition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_type_definition")) return false;
    if (!nextTokenIsFast(b, KW_ENUM)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _LEFT_, ENUM_TYPE_DEFINITION, null);
    r = consumeTokenFast(b, KW_ENUM);
    r = r && enum_definition_header(b, l + 1);
    p = r; // pin = enum_definition_header
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && report_error_(b, enum_body(b, l + 1)) && r;
    r = p && popClassIdentifier(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // <<replaceVariants "type">> fail
  static boolean expect_type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expect_type")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = replaceVariants(b, l + 1, "type");
    r = r && noMatch(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<notApplicationArguments expression_or_application_inner>>
  public static boolean expression_or_application(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expression_or_application")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = notApplicationArguments(b, l + 1, expression_or_application_inner_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // expression application*
  static boolean expression_or_application_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expression_or_application_inner")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expression(b, l + 1, -1);
    r = r && expression_or_application_inner_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // application*
  private static boolean expression_or_application_inner_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expression_or_application_inner_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!application(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "expression_or_application_inner_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // IDENTIFIER
  //                            | '!' | '(' | '+' | '++' | '-' | '--' | '[' | '~'
  //                            | 'this' | 'super'
  //                            | DOLLAR_SLASHY_BEGIN
  //                            | GSTRING_BEGIN
  //                            | SLASHY_BEGIN
  //                            | STR_DQ | STR_SQ
  //                            | 'new'
  //                            | primitive_type
  //                            | simple_literal_tokens
  static boolean expression_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expression_start")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, IDENTIFIER);
    if (!r) r = consumeTokenFast(b, T_NOT);
    if (!r) r = consumeTokenFast(b, T_LPAREN);
    if (!r) r = consumeTokenFast(b, T_PLUS);
    if (!r) r = consumeTokenFast(b, T_INC);
    if (!r) r = consumeTokenFast(b, T_MINUS);
    if (!r) r = consumeTokenFast(b, T_DEC);
    if (!r) r = consumeTokenFast(b, T_LBRACK);
    if (!r) r = consumeTokenFast(b, T_BNOT);
    if (!r) r = consumeTokenFast(b, KW_THIS);
    if (!r) r = consumeTokenFast(b, KW_SUPER);
    if (!r) r = consumeTokenFast(b, DOLLAR_SLASHY_BEGIN);
    if (!r) r = consumeTokenFast(b, GSTRING_BEGIN);
    if (!r) r = consumeTokenFast(b, SLASHY_BEGIN);
    if (!r) r = consumeTokenFast(b, STR_DQ);
    if (!r) r = consumeTokenFast(b, STR_SQ);
    if (!r) r = consumeTokenFast(b, KW_NEW);
    if (!r) r = parsePrimitiveType(b, l + 1);
    if (!r) r = simple_literal_tokens(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<extendedStatement>> mb_separators
  static boolean extended_statement_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extended_statement_item")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = extendedStatement(b, l + 1);
    r = r && mb_separators(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // mb_nl type_code_reference | extends_list_item_recovered
  static boolean extends_list_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extends_list_item")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = extends_list_item_0(b, l + 1);
    if (!r) r = extends_list_item_recovered(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // mb_nl type_code_reference
  private static boolean extends_list_item_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extends_list_item_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && type_code_reference(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // empty fail
  static boolean extends_list_item_recovered(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extends_list_item_recovered")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = empty(b, l + 1);
    p = r; // pin = 1
    r = r && noMatch(b, l + 1);
    exit_section_(b, l, m, r, p, extends_recovery_parser_);
    return r || p;
  }

  /* ********************************************************** */
  // !(',' | 'implements' | '{')
  static boolean extends_recovery(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extends_recovery")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !extends_recovery_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ',' | 'implements' | '{'
  private static boolean extends_recovery_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extends_recovery_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_COMMA);
    if (!r) r = consumeToken(b, KW_IMPLEMENTS);
    if (!r) r = consumeToken(b, T_LBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ':'
  static boolean fast_colon(PsiBuilder b, int l) {
    return consumeTokenFast(b, T_COLON);
  }

  /* ********************************************************** */
  // ','
  static boolean fast_comma(PsiBuilder b, int l) {
    return consumeTokenFast(b, T_COMMA);
  }

  /* ********************************************************** */
  // DOLLAR_SLASHY_CONTENT
  static boolean fast_dollar_slashy_content(PsiBuilder b, int l) {
    return consumeTokenFast(b, DOLLAR_SLASHY_CONTENT);
  }

  /* ********************************************************** */
  // '<'
  static boolean fast_l_angle(PsiBuilder b, int l) {
    return consumeTokenFast(b, T_LT);
  }

  /* ********************************************************** */
  // '?'
  static boolean fast_question(PsiBuilder b, int l) {
    return consumeTokenFast(b, T_Q);
  }

  /* ********************************************************** */
  // SLASHY_CONTENT
  static boolean fast_slashy_content(PsiBuilder b, int l) {
    return consumeTokenFast(b, SLASHY_CONTENT);
  }

  /* ********************************************************** */
  // GSTRING_CONTENT
  static boolean fast_string_content(PsiBuilder b, int l) {
    return consumeTokenFast(b, GSTRING_CONTENT);
  }

  /* ********************************************************** */
  // var
  public static boolean field(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = var(b, l + 1);
    exit_section_(b, m, FIELD, r);
    return r;
  }

  /* ********************************************************** */
  // field (',' mb_nl field)*
  public static boolean field_declaration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_declaration")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = field(b, l + 1);
    r = r && field_declaration_1(b, l + 1);
    exit_section_(b, m, VARIABLE_DECLARATION, r);
    return r;
  }

  // (',' mb_nl field)*
  private static boolean field_declaration_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_declaration_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!field_declaration_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "field_declaration_1", c)) break;
    }
    return true;
  }

  // ',' mb_nl field
  private static boolean field_declaration_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_declaration_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_COMMA);
    r = r && mb_nl(b, l + 1);
    r = r && field(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'finally' lazy_block
  public static boolean finally_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "finally_clause")) return false;
    if (!nextTokenIs(b, KW_FINALLY)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, FINALLY_CLAUSE, null);
    r = consumeToken(b, KW_FINALLY);
    p = r; // pin = 1
    r = r && lazy_block(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // &<<extendedStatement>> | &';' | block_or_statement
  static boolean for_body(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_body")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<loop body>");
    r = for_body_0(b, l + 1);
    if (!r) r = for_body_1(b, l + 1);
    if (!r) r = block_or_statement(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // &<<extendedStatement>>
  private static boolean for_body_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_body_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = extendedStatement(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // &';'
  private static boolean for_body_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_body_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, T_SEMI);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // for_in_clause | traditional_for_clause
  static boolean for_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_clause")) return false;
    boolean r;
    r = for_in_clause(b, l + 1);
    if (!r) r = traditional_for_clause(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // traditional_for_parameter | expression | clear_variants_and_fail
  static boolean for_clause_initialization(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_clause_initialization")) return false;
    boolean r;
    r = traditional_for_parameter(b, l + 1);
    if (!r) r = expression(b, l + 1, -1);
    if (!r) r = clear_variants_and_fail(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // '(' mb_nl for_clause mb_nl ')'
  static boolean for_header(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_header")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_LPAREN);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && report_error_(b, for_clause(b, l + 1)) && r;
    r = p && report_error_(b, mb_nl(b, l + 1)) && r;
    r = p && consumeToken(b, T_RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // for_in_parameter (':' | 'in') expression
  public static boolean for_in_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_in_clause")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FOR_IN_CLAUSE, "<for in clause>");
    r = for_in_parameter(b, l + 1);
    r = r && for_in_clause_1(b, l + 1);
    r = r && expression(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ':' | 'in'
  private static boolean for_in_clause_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_in_clause_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_COLON);
    if (!r) r = consumeTokenFast(b, KW_IN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // modifier_list mb_type_element IDENTIFIER | clear_variants_and_fail
  public static boolean for_in_parameter(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_in_parameter")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PARAMETER, "<for in parameter>");
    r = for_in_parameter_0(b, l + 1);
    if (!r) r = clear_variants_and_fail(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // modifier_list mb_type_element IDENTIFIER
  private static boolean for_in_parameter_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_in_parameter_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = modifier_list(b, l + 1);
    r = r && mb_type_element(b, l + 1);
    r = r && consumeToken(b, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'for' (for_header mb_nl for_body)
  public static boolean for_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_statement")) return false;
    if (!nextTokenIs(b, KW_FOR)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, FOR_STATEMENT, null);
    r = consumeToken(b, KW_FOR);
    p = r; // pin = 1
    r = r && for_statement_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // for_header mb_nl for_body
  private static boolean for_statement_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_statement_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = for_header(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && for_body(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '(' mb_nl expression_or_application mb_nl ')'
  static boolean if_header(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_header")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_LPAREN);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && report_error_(b, expression_or_application(b, l + 1)) && r;
    r = p && report_error_(b, mb_nl(b, l + 1)) && r;
    r = p && consumeToken(b, T_RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // 'if' after_if_keyword
  public static boolean if_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_statement")) return false;
    if (!nextTokenIs(b, KW_IF)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, IF_STATEMENT, null);
    r = consumeToken(b, KW_IF);
    p = r; // pin = 1
    r = r && after_if_keyword(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // empty fail
  static boolean implement_list_item_recovered(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "implement_list_item_recovered")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = empty(b, l + 1);
    p = r; // pin = 1
    r = r && noMatch(b, l + 1);
    exit_section_(b, l, m, r, p, implements_recovery_parser_);
    return r || p;
  }

  /* ********************************************************** */
  // mb_nl type_code_reference | implement_list_item_recovered
  static boolean implements_list_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "implements_list_item")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = implements_list_item_0(b, l + 1);
    if (!r) r = implement_list_item_recovered(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // mb_nl type_code_reference
  private static boolean implements_list_item_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "implements_list_item_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && type_code_reference(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // !(',' | '{')
  static boolean implements_recovery(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "implements_recovery")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !implements_recovery_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ',' | '{'
  private static boolean implements_recovery_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "implements_recovery_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_COMMA);
    if (!r) r = consumeToken(b, T_LBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // modifier_list mb_nl ('import') 'static'? qualified_name import_star? import_alias?
  public static boolean import_$(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "import_$")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, IMPORT, "<import $>");
    r = modifier_list(b, l + 1);
    r = r && mb_nl(b, l + 1);
    r = r && import_2(b, l + 1);
    p = r; // pin = 3
    r = r && report_error_(b, import_3(b, l + 1));
    r = p && report_error_(b, qualified_name(b, l + 1)) && r;
    r = p && report_error_(b, import_5(b, l + 1)) && r;
    r = p && import_6(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ('import')
  private static boolean import_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "import_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, KW_IMPORT);
    exit_section_(b, m, null, r);
    return r;
  }

  // 'static'?
  private static boolean import_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "import_3")) return false;
    consumeTokenFast(b, KW_STATIC);
    return true;
  }

  // import_star?
  private static boolean import_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "import_5")) return false;
    import_star(b, l + 1);
    return true;
  }

  // import_alias?
  private static boolean import_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "import_6")) return false;
    import_alias(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // 'as' mb_nl IDENTIFIER
  public static boolean import_alias(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "import_alias")) return false;
    if (!nextTokenIs(b, KW_AS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, IMPORT_ALIAS, null);
    r = consumeToken(b, KW_AS);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && consumeToken(b, IDENTIFIER) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '.' '*'
  static boolean import_star(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "import_star")) return false;
    if (!nextTokenIs(b, T_DOT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, T_DOT, T_STAR);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // !empty_map '[' bracket_argument_list_inner ']'
  public static boolean index_expression_argument_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "index_expression_argument_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ARGUMENT_LIST, "<index expression argument list>");
    r = index_expression_argument_list_0(b, l + 1);
    r = r && consumeTokenFast(b, T_LBRACK);
    p = r; // pin = 2
    r = r && report_error_(b, bracket_argument_list_inner(b, l + 1));
    r = p && consumeToken(b, T_RBRACK) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // !empty_map
  private static boolean index_expression_argument_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "index_expression_argument_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !empty_map(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // mb_not ('instanceof') (type_element | expect_type)
  static boolean instanceof_expression_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "instanceof_expression_tail")) return false;
    if (!nextTokenIsFast(b, T_NOT, KW_INSTANCEOF)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = mb_not(b, l + 1);
    r = r && instanceof_expression_tail_1(b, l + 1);
    p = r; // pin = 2
    r = r && instanceof_expression_tail_2(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ('instanceof')
  private static boolean instanceof_expression_tail_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "instanceof_expression_tail_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, KW_INSTANCEOF);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_element | expect_type
  private static boolean instanceof_expression_tail_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "instanceof_expression_tail_2")) return false;
    boolean r;
    r = type_element(b, l + 1);
    if (!r) r = expect_type(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // <<classIdentifier>> type_parameter_list? nl_extends nl_non_empty_implements?
  static boolean interface_definition_header(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "interface_definition_header")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = classIdentifier(b, l + 1);
    r = r && interface_definition_header_1(b, l + 1);
    r = r && nl_extends(b, l + 1);
    r = r && interface_definition_header_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_parameter_list?
  private static boolean interface_definition_header_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "interface_definition_header_1")) return false;
    type_parameter_list(b, l + 1);
    return true;
  }

  // nl_non_empty_implements?
  private static boolean interface_definition_header_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "interface_definition_header_3")) return false;
    nl_non_empty_implements(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // 'interface' interface_definition_header mb_nl class_body <<popClassIdentifier>>
  public static boolean interface_type_definition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "interface_type_definition")) return false;
    if (!nextTokenIsFast(b, KW_INTERFACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _LEFT_, INTERFACE_TYPE_DEFINITION, null);
    r = consumeTokenFast(b, KW_INTERFACE);
    r = r && interface_definition_header(b, l + 1);
    p = r; // pin = interface_definition_header
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && report_error_(b, class_body(b, l + 1)) && r;
    r = p && popClassIdentifier(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // IDENTIFIER after_label
  public static boolean labeled_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "labeled_statement")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && after_label(b, l + 1);
    exit_section_(b, m, LABELED_STATEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // <<parseBlockLazy open_block 'OPEN_BLOCK'>>
  public static boolean lazy_block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lazy_block")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<lazy block>");
    r = parseBlockLazy(b, l + 1, open_block_parser_, OPEN_BLOCK);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<parseBlockLazy constructor_block 'CONSTRUCTOR_BLOCK'>>
  public static boolean lazy_constructor_block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lazy_constructor_block")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<lazy constructor block>");
    r = parseBlockLazy(b, l + 1, constructor_block_parser_, CONSTRUCTOR_BLOCK);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '<' '<'
  public static boolean left_shift_sign(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "left_shift_sign")) return false;
    if (!nextTokenIsFast(b, T_LT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, T_LT, T_LT);
    exit_section_(b, m, LEFT_SHIFT_SIGN, r);
    return r;
  }

  /* ********************************************************** */
  // <<parseTailLeftFlat block_declaration_start variable_declaration_tail>>
  static boolean local_variable_declaration(PsiBuilder b, int l) {
    return parseTailLeftFlat(b, l + 1, block_declaration_start_parser_, variable_declaration_tail_parser_);
  }

  /* ********************************************************** */
  // '[' expression ']'
  static boolean mandatory_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mandatory_expression")) return false;
    if (!nextTokenIsFast(b, T_LBRACK)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokenFast(b, T_LBRACK);
    p = r; // pin = 1
    r = r && report_error_(b, expression(b, l + 1, -1));
    r = p && consumeToken(b, T_RBRACK) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // [mb_nl declaration_type_element]
  static boolean mb_declaration_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mb_declaration_type_element")) return false;
    mb_declaration_type_element_0(b, l + 1);
    return true;
  }

  // mb_nl declaration_type_element
  private static boolean mb_declaration_type_element_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mb_declaration_type_element_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && declaration_type_element(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ['=' mb_nl expression_or_application]
  static boolean mb_initializer(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mb_initializer")) return false;
    mb_initializer_0(b, l + 1);
    return true;
  }

  // '=' mb_nl expression_or_application
  private static boolean mb_initializer_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mb_initializer_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokenFast(b, T_ASSIGN);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && expression_or_application(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // nl?
  static boolean mb_nl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mb_nl")) return false;
    nl(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // <<something>> | <<withProtectedLastVariantPos (nl <<something>>)>>
  static boolean mb_nl_group(PsiBuilder b, int l, Parser _something) {
    if (!recursion_guard_(b, l, "mb_nl_group")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _something.parse(b, l);
    if (!r) r = withProtectedLastVariantPos(b, l + 1, new Parser() {
      public boolean parse(PsiBuilder b, int l) {
        return mb_nl_group_1_0(b, l + 1, _something);
      }
    });
    exit_section_(b, m, null, r);
    return r;
  }

  // nl <<something>>
  private static boolean mb_nl_group_1_0(PsiBuilder b, int l, Parser _something) {
    if (!recursion_guard_(b, l, "mb_nl_group_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = nl(b, l + 1);
    r = r && _something.parse(b, l);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '!'?
  static boolean mb_not(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mb_not")) return false;
    consumeTokenFast(b, T_NOT);
    return true;
  }

  /* ********************************************************** */
  // '?'?
  static boolean mb_question(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mb_question")) return false;
    consumeTokenFast(b, T_Q);
    return true;
  }

  /* ********************************************************** */
  // separator*
  static boolean mb_separators(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mb_separators")) return false;
    while (true) {
      int c = current_position_(b);
      if (!separator(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "mb_separators", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // type_element_followed_by_identifier?
  static boolean mb_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mb_type_element")) return false;
    type_element_followed_by_identifier(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // [mb_nl type_parameter_list]
  static boolean mb_type_parameter_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mb_type_parameter_list")) return false;
    mb_type_parameter_list_0(b, l + 1);
    return true;
  }

  // mb_nl type_parameter_list
  private static boolean mb_type_parameter_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mb_type_parameter_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && type_parameter_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // method_identifier parse_parameter_list nl_throws [mb_nl lazy_block]
  public static boolean method(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, METHOD, "<method>");
    r = method_identifier(b, l + 1);
    r = r && parse_parameter_list(b, l + 1);
    p = r; // pin = parse_parameter_list
    r = r && report_error_(b, nl_throws(b, l + 1));
    r = p && method_3(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // [mb_nl lazy_block]
  private static boolean method_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_3")) return false;
    method_3_0(b, l + 1);
    return true;
  }

  // mb_nl lazy_block
  private static boolean method_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && lazy_block(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER | STR_SQ | STR_DQ
  static boolean method_identifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_identifier")) return false;
    boolean r;
    r = consumeTokenFast(b, IDENTIFIER);
    if (!r) r = consumeTokenFast(b, STR_SQ);
    if (!r) r = consumeTokenFast(b, STR_DQ);
    return r;
  }

  /* ********************************************************** */
  // method_identifier '('
  static boolean method_lookahead(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_lookahead")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = method_identifier(b, l + 1);
    r = r && consumeToken(b, T_LPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<paren_list_inner parse_parameter>>
  public static boolean method_parameter_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_parameter_list")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, PARAMETER_LIST, "<method parameter list>");
    r = paren_list_inner(b, l + 1, parse_parameter_parser_);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '.&' | '::'
  static boolean method_reference_dot(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_reference_dot")) return false;
    if (!nextTokenIsFast(b, T_METHOD_CLOSURE, T_METHOD_REFERENCE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_METHOD_CLOSURE);
    if (!r) r = consumeTokenFast(b, T_METHOD_REFERENCE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // constructor | method
  static boolean methods_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "methods_tail")) return false;
    boolean r;
    r = constructor(b, l + 1);
    if (!r) r = method(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // 'abstract'
  //                    | 'default'
  //                    | 'final'
  //                    | 'native'
  //                    | 'private'
  //                    | 'protected'
  //                    | 'public'
  //                    | 'static'
  //                    | 'strictfp'
  //                    | 'synchronized' !'('
  //                    | 'transient'
  //                    | 'volatile'
  //                    | def_modifier
  static boolean modifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "modifier")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, KW_ABSTRACT);
    if (!r) r = consumeTokenFast(b, KW_DEFAULT);
    if (!r) r = consumeTokenFast(b, KW_FINAL);
    if (!r) r = consumeTokenFast(b, KW_NATIVE);
    if (!r) r = consumeTokenFast(b, KW_PRIVATE);
    if (!r) r = consumeTokenFast(b, KW_PROTECTED);
    if (!r) r = consumeTokenFast(b, KW_PUBLIC);
    if (!r) r = consumeTokenFast(b, KW_STATIC);
    if (!r) r = consumeTokenFast(b, KW_STRICTFP);
    if (!r) r = modifier_9(b, l + 1);
    if (!r) r = consumeTokenFast(b, KW_TRANSIENT);
    if (!r) r = consumeTokenFast(b, KW_VOLATILE);
    if (!r) r = def_modifier(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // 'synchronized' !'('
  private static boolean modifier_9(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "modifier_9")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, KW_SYNCHRONIZED);
    r = r && modifier_9_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // !'('
  private static boolean modifier_9_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "modifier_9_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeTokenFast(b, T_LPAREN);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // non_empty_modifier_list | empty_modifier_list
  public static boolean modifier_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "modifier_list")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, MODIFIER_LIST, "<modifier list>");
    r = non_empty_modifier_list(b, l + 1);
    if (!r) r = empty_modifier_list(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // tuple tuple_initializer
  static boolean multi_tuple_assignment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "multi_tuple_assignment")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = tuple(b, l + 1);
    p = r; // pin = tuple
    r = r && tuple_initializer(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // empty_modifier_list &method_lookahead
  static boolean naked_method_declaration_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "naked_method_declaration_start")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = empty_modifier_list(b, l + 1);
    r = r && naked_method_declaration_start_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &method_lookahead
  private static boolean naked_method_declaration_start_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "naked_method_declaration_start_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = method_lookahead(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // argument_label named_argument_tail
  public static boolean named_argument(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "named_argument")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, NAMED_ARGUMENT, "<named argument>");
    r = argument_label(b, l + 1);
    r = r && named_argument_tail(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // ':' expression
  static boolean named_argument_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "named_argument_tail")) return false;
    if (!nextTokenIsFast(b, T_COLON)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokenFast(b, T_COLON);
    p = r; // pin = 1
    r = r && expression(b, l + 1, -1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // new_expression_type (call_argument_list_start call_argument_list | array_declaration)
  static boolean new_expression_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "new_expression_tail")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = new_expression_type(b, l + 1);
    p = r; // pin = 1
    r = r && new_expression_tail_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // call_argument_list_start call_argument_list | array_declaration
  private static boolean new_expression_tail_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "new_expression_tail_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = new_expression_tail_1_0(b, l + 1);
    if (!r) r = array_declaration(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // call_argument_list_start call_argument_list
  private static boolean new_expression_tail_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "new_expression_tail_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = call_argument_list_start(b, l + 1);
    r = r && call_argument_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // primitive_type_element | <<allowDiamond code_reference>>
  static boolean new_expression_type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "new_expression_type")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = primitive_type_element(b, l + 1);
    if (!r) r = allowDiamond(b, l + 1, code_reference_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // NL
  static boolean nl(PsiBuilder b, int l) {
    return consumeTokenFast(b, NL);
  }

  /* ********************************************************** */
  // nl_non_empty_extends | empty_extends_clause
  static boolean nl_extends(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nl_extends")) return false;
    boolean r;
    r = nl_non_empty_extends(b, l + 1);
    if (!r) r = empty_extends_clause(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // nl_non_empty_implements | empty_implements_clause
  static boolean nl_implements(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nl_implements")) return false;
    boolean r;
    r = nl_non_empty_implements(b, l + 1);
    if (!r) r = empty_implements_clause(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // mb_nl non_empty_extends_clause
  static boolean nl_non_empty_extends(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nl_non_empty_extends")) return false;
    if (!nextTokenIsFast(b, KW_EXTENDS, NL)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && non_empty_extends_clause(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // mb_nl non_empty_implements_clause
  static boolean nl_non_empty_implements(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nl_non_empty_implements")) return false;
    if (!nextTokenIsFast(b, KW_IMPLEMENTS, NL)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && non_empty_implements_clause(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // [nl &('throws' | '{')] (non_empty_throws_clause | empty_throws_clause)
  static boolean nl_throws(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nl_throws")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = nl_throws_0(b, l + 1);
    r = r && nl_throws_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [nl &('throws' | '{')]
  private static boolean nl_throws_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nl_throws_0")) return false;
    nl_throws_0_0(b, l + 1);
    return true;
  }

  // nl &('throws' | '{')
  private static boolean nl_throws_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nl_throws_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = nl(b, l + 1);
    r = r && nl_throws_0_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &('throws' | '{')
  private static boolean nl_throws_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nl_throws_0_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = nl_throws_0_0_1_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // 'throws' | '{'
  private static boolean nl_throws_0_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nl_throws_0_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_THROWS);
    if (!r) r = consumeToken(b, T_LBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // non_empty_throws_clause | empty_throws_clause
  private static boolean nl_throws_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nl_throws_1")) return false;
    boolean r;
    r = non_empty_throws_clause(b, l + 1);
    if (!r) r = empty_throws_clause(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // 'extends' <<comma_list_p extends_list_item>>
  public static boolean non_empty_extends_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "non_empty_extends_clause")) return false;
    if (!nextTokenIsFast(b, KW_EXTENDS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, EXTENDS_CLAUSE, null);
    r = consumeTokenFast(b, KW_EXTENDS);
    p = r; // pin = 1
    r = r && comma_list_p(b, l + 1, extends_list_item_parser_);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // 'implements' <<comma_list_p implements_list_item>>
  public static boolean non_empty_implements_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "non_empty_implements_clause")) return false;
    if (!nextTokenIsFast(b, KW_IMPLEMENTS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, IMPLEMENTS_CLAUSE, null);
    r = consumeTokenFast(b, KW_IMPLEMENTS);
    p = r; // pin = 1
    r = r && comma_list_p(b, l + 1, implements_list_item_parser_);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '[' bracket_argument_list_item bracket_argument_list_inner ']'
  static boolean non_empty_list_or_map(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "non_empty_list_or_map")) return false;
    if (!nextTokenIs(b, T_LBRACK)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_LBRACK);
    r = r && bracket_argument_list_item(b, l + 1);
    p = r; // pin = 2
    r = r && report_error_(b, bracket_argument_list_inner(b, l + 1));
    r = p && consumeToken(b, T_RBRACK) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // <<a_b_a (modifier | annotation) mb_nl>>
  public static boolean non_empty_modifier_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "non_empty_modifier_list")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MODIFIER_LIST, "<non empty modifier list>");
    r = a_b_a(b, l + 1, non_empty_modifier_list_0_0_parser_, mb_nl_parser_);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // modifier | annotation
  private static boolean non_empty_modifier_list_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "non_empty_modifier_list_0_0")) return false;
    boolean r;
    r = modifier(b, l + 1);
    if (!r) r = annotation(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // 'throws' <<comma_list_p throws_list_item>>
  public static boolean non_empty_throws_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "non_empty_throws_clause")) return false;
    if (!nextTokenIsFast(b, KW_THROWS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, THROWS_CLAUSE, null);
    r = consumeTokenFast(b, KW_THROWS);
    p = r; // pin = 1
    r = r && comma_list_p(b, l + 1, throws_list_item_parser_);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '<' <<comma_list_p type_argument_list_item>> type_argument_list_end
  static boolean non_empty_type_argument_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "non_empty_type_argument_list")) return false;
    if (!nextTokenIsFast(b, T_LT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokenFast(b, T_LT);
    p = r; // pin = 1
    r = r && report_error_(b, comma_list_p(b, l + 1, type_argument_list_item_parser_));
    r = p && type_argument_list_end(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // !fast_colon
  static boolean not_colon(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "not_colon")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !fast_colon(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '{' mb_separators block_levels '}'
  public static boolean open_block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "open_block")) return false;
    if (!nextTokenIs(b, T_LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, OPEN_BLOCK, null);
    r = consumeToken(b, T_LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, mb_separators(b, l + 1));
    r = p && report_error_(b, block_levels(b, l + 1)) && r;
    r = p && consumeToken(b, T_RBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '[' expression? ']'
  static boolean optional_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "optional_expression")) return false;
    if (!nextTokenIsFast(b, T_LBRACK)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokenFast(b, T_LBRACK);
    p = r; // pin = 1
    r = r && report_error_(b, optional_expression_1(b, l + 1));
    r = p && consumeToken(b, T_RBRACK) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // expression?
  private static boolean optional_expression_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "optional_expression_1")) return false;
    expression(b, l + 1, -1);
    return true;
  }

  /* ********************************************************** */
  // '(' expression ')'
  static boolean p_parenthesized_expression_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "p_parenthesized_expression_inner")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_LPAREN);
    p = r; // pin = 1
    r = r && report_error_(b, expression(b, l + 1, -1));
    r = p && consumeToken(b, T_RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // modifier_list mb_nl ('package') package_name
  public static boolean package_definition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "package_definition")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PACKAGE_DEFINITION, "<package definition>");
    r = modifier_list(b, l + 1);
    r = r && mb_nl(b, l + 1);
    r = r && package_definition_2(b, l + 1);
    p = r; // pin = 3
    r = r && package_name(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ('package')
  private static boolean package_definition_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "package_definition_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, KW_PACKAGE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // qualified_name
  static boolean package_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "package_name")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<package name>");
    r = qualified_name(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // var
  public static boolean parameter(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = var(b, l + 1);
    exit_section_(b, m, PARAMETER, r);
    return r;
  }

  /* ********************************************************** */
  // parameter_start_modifiers | parameter_start_no_modifiers
  static boolean parameter_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_start")) return false;
    boolean r;
    r = parameter_start_modifiers(b, l + 1);
    if (!r) r = parameter_start_no_modifiers(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // parameter_type_element_silent? ellipsis?
  static boolean parameter_start_after_modifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_start_after_modifiers")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = parameter_start_after_modifiers_0(b, l + 1);
    r = r && parameter_start_after_modifiers_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // parameter_type_element_silent?
  private static boolean parameter_start_after_modifiers_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_start_after_modifiers_0")) return false;
    parameter_type_element_silent(b, l + 1);
    return true;
  }

  // ellipsis?
  private static boolean parameter_start_after_modifiers_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_start_after_modifiers_1")) return false;
    ellipsis(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // parameter_type_element_silent ellipsis? | ellipsis | &IDENTIFIER
  static boolean parameter_start_after_no_modifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_start_after_no_modifiers")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = parameter_start_after_no_modifiers_0(b, l + 1);
    if (!r) r = ellipsis(b, l + 1);
    if (!r) r = parameter_start_after_no_modifiers_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // parameter_type_element_silent ellipsis?
  private static boolean parameter_start_after_no_modifiers_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_start_after_no_modifiers_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = parameter_type_element_silent(b, l + 1);
    r = r && parameter_start_after_no_modifiers_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ellipsis?
  private static boolean parameter_start_after_no_modifiers_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_start_after_no_modifiers_0_1")) return false;
    ellipsis(b, l + 1);
    return true;
  }

  // &IDENTIFIER
  private static boolean parameter_start_after_no_modifiers_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_start_after_no_modifiers_2")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // non_empty_modifier_list parameter_start_after_modifiers
  static boolean parameter_start_modifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_start_modifiers")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = non_empty_modifier_list(b, l + 1);
    p = r; // pin = 1
    r = r && parameter_start_after_modifiers(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // empty_modifier_list parameter_start_after_no_modifiers
  static boolean parameter_start_no_modifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_start_no_modifiers")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = empty_modifier_list(b, l + 1);
    r = r && parameter_start_after_no_modifiers(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // primitive_type_element array_type_elements
  //                                  | qualified_class_type_element array_type_elements
  //                                  | unqualified_class_type_element &(IDENTIFIER | ellipsis)
  //                                  | unqualified_class_type_element array_type_element array_type_elements
  static boolean parameter_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<type>");
    r = parameter_type_element_0(b, l + 1);
    if (!r) r = parameter_type_element_1(b, l + 1);
    if (!r) r = parameter_type_element_2(b, l + 1);
    if (!r) r = parameter_type_element_3(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // primitive_type_element array_type_elements
  private static boolean parameter_type_element_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_type_element_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = primitive_type_element(b, l + 1);
    r = r && array_type_elements(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // qualified_class_type_element array_type_elements
  private static boolean parameter_type_element_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_type_element_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = qualified_class_type_element(b, l + 1);
    r = r && array_type_elements(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // unqualified_class_type_element &(IDENTIFIER | ellipsis)
  private static boolean parameter_type_element_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_type_element_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = unqualified_class_type_element(b, l + 1);
    r = r && parameter_type_element_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &(IDENTIFIER | ellipsis)
  private static boolean parameter_type_element_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_type_element_2_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = parameter_type_element_2_1_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // IDENTIFIER | ellipsis
  private static boolean parameter_type_element_2_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_type_element_2_1_0")) return false;
    boolean r;
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = ellipsis(b, l + 1);
    return r;
  }

  // unqualified_class_type_element array_type_element array_type_elements
  private static boolean parameter_type_element_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_type_element_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = unqualified_class_type_element(b, l + 1);
    r = r && array_type_element(b, l + 1);
    r = r && array_type_elements(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // parameter_type_element | clear_variants_and_fail
  static boolean parameter_type_element_silent(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameter_type_element_silent")) return false;
    boolean r;
    r = parameter_type_element(b, l + 1);
    if (!r) r = clear_variants_and_fail(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // paren_argument_list_item* clear_error
  static boolean paren_argument_list_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paren_argument_list_inner")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = paren_argument_list_inner_0(b, l + 1);
    r = r && clearError(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // paren_argument_list_item*
  private static boolean paren_argument_list_inner_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paren_argument_list_inner_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!paren_argument_list_item(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "paren_argument_list_inner_0", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // <<argument_list_item ')'>>
  static boolean paren_argument_list_item(PsiBuilder b, int l) {
    return argument_list_item(b, l + 1, T_RPAREN_parser_);
  }

  /* ********************************************************** */
  // '('')' | '(' <<paren_list_inner <<item>>>> ')'
  static boolean paren_list(PsiBuilder b, int l, Parser _item) {
    if (!recursion_guard_(b, l, "paren_list")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = parseTokens(b, 0, T_LPAREN, T_RPAREN);
    if (!r) r = paren_list_1(b, l + 1, _item);
    exit_section_(b, m, null, r);
    return r;
  }

  // '(' <<paren_list_inner <<item>>>> ')'
  private static boolean paren_list_1(PsiBuilder b, int l, Parser _item) {
    if (!recursion_guard_(b, l, "paren_list_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_LPAREN);
    p = r; // pin = 1
    r = r && report_error_(b, paren_list_inner(b, l + 1, _item));
    r = p && consumeToken(b, T_RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // <<paren_list_item <<item>>>> <<paren_list_tail <<item>>>>*
  static boolean paren_list_inner(PsiBuilder b, int l, Parser _item) {
    if (!recursion_guard_(b, l, "paren_list_inner")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = paren_list_item(b, l + 1, _item);
    r = r && paren_list_inner_1(b, l + 1, _item);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<paren_list_tail <<item>>>>*
  private static boolean paren_list_inner_1(PsiBuilder b, int l, Parser _item) {
    if (!recursion_guard_(b, l, "paren_list_inner_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!paren_list_tail(b, l + 1, _item)) break;
      if (!empty_element_parsed_guard_(b, "paren_list_inner_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // empty <<item>>
  static boolean paren_list_item(PsiBuilder b, int l, Parser _item) {
    if (!recursion_guard_(b, l, "paren_list_item")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = empty(b, l + 1);
    p = r; // pin = 1
    r = r && _item.parse(b, l);
    exit_section_(b, l, m, r, p, paren_list_item_recovery_parser_);
    return r || p;
  }

  /* ********************************************************** */
  // !(',' | ')' | '}')
  static boolean paren_list_item_recovery(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paren_list_item_recovery")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !paren_list_item_recovery_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ',' | ')' | '}'
  private static boolean paren_list_item_recovery_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paren_list_item_recovery_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_COMMA);
    if (!r) r = consumeToken(b, T_RPAREN);
    if (!r) r = consumeToken(b, T_RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ',' <<paren_list_item <<item>>>>
  static boolean paren_list_tail(PsiBuilder b, int l, Parser _item) {
    if (!recursion_guard_(b, l, "paren_list_tail")) return false;
    if (!nextTokenIs(b, T_COMMA)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_COMMA);
    p = r; // pin = 1
    r = r && paren_list_item(b, l + 1, _item);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // <<parseTailLeftFlat class_declaration_start annotation_tails>>
  //                                        | <<parseTailLeftFlat naked_method_declaration_start annotation_method>>
  static boolean parse_annotation_declaration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parse_annotation_declaration")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = parseTailLeftFlat(b, l + 1, class_declaration_start_parser_, annotation_tails_parser_);
    if (!r) r = parseTailLeftFlat(b, l + 1, naked_method_declaration_start_parser_, annotation_method_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<parseArgument argument>>
  static boolean parse_argument(PsiBuilder b, int l) {
    return parseArgument(b, l + 1, argument_parser_);
  }

  /* ********************************************************** */
  // <<parseTailLeftFlat block_declaration_start block_declaration_tail>>
  static boolean parse_block_declaration(PsiBuilder b, int l) {
    return parseTailLeftFlat(b, l + 1, block_declaration_start_parser_, block_declaration_tail_parser_);
  }

  /* ********************************************************** */
  // <<parseTailLeftFlat catch_parameter_start catch_parameter>>
  static boolean parse_catch_parameter(PsiBuilder b, int l) {
    return parseTailLeftFlat(b, l + 1, catch_parameter_start_parser_, catch_parameter_parser_);
  }

  /* ********************************************************** */
  // <<parseTailLeftFlat class_declaration_start class_declaration_tail>>
  //                                   | <<parseTailLeftFlat naked_method_declaration_start methods_tail>>
  static boolean parse_class_declaration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parse_class_declaration")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = parseTailLeftFlat(b, l + 1, class_declaration_start_parser_, class_declaration_tail_parser_);
    if (!r) r = parseTailLeftFlat(b, l + 1, naked_method_declaration_start_parser_, methods_tail_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '(' empty_parameter_list ')'
  static boolean parse_empty_parameter_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parse_empty_parameter_list")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_LPAREN);
    r = r && empty_parameter_list(b, l + 1);
    r = r && consumeToken(b, T_RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '(' method_parameter_list ')'
  static boolean parse_non_empty_parameter_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parse_non_empty_parameter_list")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_LPAREN);
    p = r; // pin = 1
    r = r && report_error_(b, method_parameter_list(b, l + 1));
    r = p && consumeToken(b, T_RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // <<parseTailLeftFlat parameter_start parameter>>
  static boolean parse_parameter(PsiBuilder b, int l) {
    return parseTailLeftFlat(b, l + 1, parameter_start_parser_, parameter_parser_);
  }

  /* ********************************************************** */
  // parse_empty_parameter_list | parse_non_empty_parameter_list
  static boolean parse_parameter_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parse_parameter_list")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r;
    r = parse_empty_parameter_list(b, l + 1);
    if (!r) r = parse_non_empty_parameter_list(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // primitive_type <<setTypeWasPrimitive>>
  public static boolean primitive_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "primitive_type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PRIMITIVE_TYPE_ELEMENT, "<type>");
    r = parsePrimitiveType(b, l + 1);
    r = r && setTypeWasPrimitive(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // reference_dot | method_reference_dot
  static boolean property_dot(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_dot")) return false;
    boolean r;
    r = reference_dot(b, l + 1);
    if (!r) r = method_reference_dot(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // parenthesized_expression | lazy_block | gstring | regex
  static boolean property_expression_identifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_expression_identifiers")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<property selector>");
    r = parenthesized_expression(b, l + 1);
    if (!r) r = lazy_block(b, l + 1);
    if (!r) r = gstring(b, l + 1);
    if (!r) r = regex(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // qualified_code_reference
  public static boolean qualified_class_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_class_type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CLASS_TYPE_ELEMENT, "<type>");
    r = qualified_code_reference(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // short_code_reference code_reference_tail code_reference_tail*
  public static boolean qualified_code_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_code_reference")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, CODE_REFERENCE, "<qualified code reference>");
    r = short_code_reference(b, l + 1);
    r = r && code_reference_tail(b, l + 1);
    r = r && qualified_code_reference_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // code_reference_tail*
  private static boolean qualified_code_reference_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_code_reference_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!code_reference_tail(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "qualified_code_reference_2", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // <<qualifiedName code_reference>>
  static boolean qualified_name(PsiBuilder b, int l) {
    return qualifiedName(b, l + 1, code_reference_parser_);
  }

  /* ********************************************************** */
  // IDENTIFIER
  //                                                      | STR_SQ | STR_DQ
  //                                                      | regex_literal
  //                                                      | 'true'
  //                                                      | 'false'
  //                                                      | 'null'
  //                                                      | 'new'
  //                                                      | 'class' | 'interface' | 'trait' | 'enum'
  //                                                      | 'this' | 'super'
  //                                                      | 'case'
  //                                                      | 'as' | 'in'
  //                                                      | 'else'
  //                                                      | modifier
  //                                                      | statement_keywords
  //                                                      | primitive_type
  static boolean qualified_reference_expression_identifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_reference_expression_identifiers")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<property selector>");
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = consumeToken(b, STR_SQ);
    if (!r) r = consumeToken(b, STR_DQ);
    if (!r) r = regex_literal(b, l + 1);
    if (!r) r = consumeToken(b, KW_TRUE);
    if (!r) r = consumeToken(b, KW_FALSE);
    if (!r) r = consumeToken(b, KW_NULL);
    if (!r) r = consumeToken(b, KW_NEW);
    if (!r) r = consumeToken(b, KW_CLASS);
    if (!r) r = consumeToken(b, KW_INTERFACE);
    if (!r) r = consumeToken(b, KW_TRAIT);
    if (!r) r = consumeToken(b, KW_ENUM);
    if (!r) r = consumeToken(b, KW_THIS);
    if (!r) r = consumeToken(b, KW_SUPER);
    if (!r) r = consumeToken(b, KW_CASE);
    if (!r) r = consumeToken(b, KW_AS);
    if (!r) r = consumeToken(b, KW_IN);
    if (!r) r = consumeToken(b, KW_ELSE);
    if (!r) r = modifier(b, l + 1);
    if (!r) r = statement_keywords(b, l + 1);
    if (!r) r = parsePrimitiveType(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '.' attribute_dot? | '?.' attribute_dot? | '*.'
  static boolean reference_dot(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reference_dot")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = reference_dot_0(b, l + 1);
    if (!r) r = reference_dot_1(b, l + 1);
    if (!r) r = consumeTokenFast(b, T_SPREAD_DOT);
    exit_section_(b, m, null, r);
    return r;
  }

  // '.' attribute_dot?
  private static boolean reference_dot_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reference_dot_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_DOT);
    r = r && reference_dot_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // attribute_dot?
  private static boolean reference_dot_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reference_dot_0_1")) return false;
    attribute_dot(b, l + 1);
    return true;
  }

  // '?.' attribute_dot?
  private static boolean reference_dot_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reference_dot_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_SAFE_DOT);
    r = r && reference_dot_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // attribute_dot?
  private static boolean reference_dot_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reference_dot_1_1")) return false;
    attribute_dot(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // slashy_literal | dollar_slashy_literal
  static boolean regex_literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "regex_literal")) return false;
    if (!nextTokenIsFast(b, DOLLAR_SLASHY_BEGIN, SLASHY_BEGIN)) return false;
    boolean r;
    r = slashy_literal(b, l + 1);
    if (!r) r = dollar_slashy_literal(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // '<' !'<' | '<=' | '>' !'>' | '>='
  static boolean relational_operator(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relational_operator")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = relational_operator_0(b, l + 1);
    if (!r) r = consumeTokenFast(b, T_LE);
    if (!r) r = relational_operator_2(b, l + 1);
    if (!r) r = consumeTokenFast(b, T_GE);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<' !'<'
  private static boolean relational_operator_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relational_operator_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_LT);
    r = r && relational_operator_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // !'<'
  private static boolean relational_operator_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relational_operator_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeTokenFast(b, T_LT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '>' !'>'
  private static boolean relational_operator_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relational_operator_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_GT);
    r = r && relational_operator_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // !'>'
  private static boolean relational_operator_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relational_operator_2_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeTokenFast(b, T_GT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'return' expression?
  public static boolean return_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "return_statement")) return false;
    if (!nextTokenIs(b, KW_RETURN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_RETURN);
    r = r && return_statement_1(b, l + 1);
    exit_section_(b, m, RETURN_STATEMENT, r);
    return r;
  }

  // expression?
  private static boolean return_statement_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "return_statement_1")) return false;
    expression(b, l + 1, -1);
    return true;
  }

  /* ********************************************************** */
  // '>' '>'
  public static boolean right_shift_sign(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "right_shift_sign")) return false;
    if (!nextTokenIsFast(b, T_GT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, T_GT, T_GT);
    exit_section_(b, m, RIGHT_SHIFT_SIGN, r);
    return r;
  }

  /* ********************************************************** */
  // '>' '>' '>'
  public static boolean right_shift_unsigned_sign(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "right_shift_unsigned_sign")) return false;
    if (!nextTokenIsFast(b, T_GT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, T_GT, T_GT, T_GT);
    exit_section_(b, m, RIGHT_SHIFT_UNSIGNED_SIGN, r);
    return r;
  }

  /* ********************************************************** */
  // mb_nl package_definition? mb_separators top_levels
  static boolean root(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && root_1(b, l + 1);
    r = r && mb_separators(b, l + 1);
    r = r && top_levels(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // package_definition?
  private static boolean root_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_1")) return false;
    package_definition(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // <<separated_item_head <<item_end>> <<element>> <<separated_recovery <<element_start>> <<item_end>>>>>> <<item_end>>
  static boolean separated_item(PsiBuilder b, int l, Parser _item_end, Parser _element, Parser _element_start) {
    if (!recursion_guard_(b, l, "separated_item")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = separated_item_head(b, l + 1, _item_end, _element, new Parser() {
      public boolean parse(PsiBuilder b, int l) {
        return separated_recovery(b, l + 1, _element_start, _item_end);
      }
    });
    p = r; // pin = 1
    r = r && _item_end.parse(b, l);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // !<<item_end>> <<element>>
  static boolean separated_item_head(PsiBuilder b, int l, Parser _item_end, Parser _element, Parser _separated_recovery) {
    if (!recursion_guard_(b, l, "separated_item_head")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = separated_item_head_0(b, l + 1, _item_end, _separated_recovery);
    p = r; // pin = 1
    r = r && _element.parse(b, l);
    exit_section_(b, l, m, r, p, _separated_recovery);
    return r || p;
  }

  // !<<item_end>>
  private static boolean separated_item_head_0(PsiBuilder b, int l, Parser _item_end, Parser _separated_recovery) {
    if (!recursion_guard_(b, l, "separated_item_head_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !_item_end.parse(b, l);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // !(<<item_end>> | <<element_start>>)
  static boolean separated_recovery(PsiBuilder b, int l, Parser _item_end, Parser _element_start) {
    if (!recursion_guard_(b, l, "separated_recovery")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !separated_recovery_0(b, l + 1, _item_end, _element_start);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<item_end>> | <<element_start>>
  private static boolean separated_recovery_0(PsiBuilder b, int l, Parser _item_end, Parser _element_start) {
    if (!recursion_guard_(b, l, "separated_recovery_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _item_end.parse(b, l);
    if (!r) r = _element_start.parse(b, l);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ';' | NL | <<extendedSeparator>>
  static boolean separator(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "separator")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_SEMI);
    if (!r) r = consumeToken(b, NL);
    if (!r) r = extendedSeparator(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // separator+
  static boolean separators(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "separators")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = separator(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!separator(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "separators", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // left_shift_sign | right_shift_unsigned_sign | right_shift_sign
  static boolean shift_sign(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shift_sign")) return false;
    if (!nextTokenIsFast(b, T_LT, T_GT)) return false;
    boolean r;
    r = left_shift_sign(b, l + 1);
    if (!r) r = right_shift_unsigned_sign(b, l + 1);
    if (!r) r = right_shift_sign(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // code_reference_part
  public static boolean short_code_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "short_code_reference")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, CODE_REFERENCE, "<short code reference>");
    r = code_reference_part(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // code_reference_identifier
  public static boolean short_simple_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "short_simple_reference")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, CODE_REFERENCE, "<short simple reference>");
    r = code_reference_identifier(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // NUM_INT
  //                                 | NUM_LONG
  //                                 | NUM_BIG_INT
  //                                 | NUM_BIG_DECIMAL
  //                                 | NUM_FLOAT
  //                                 | NUM_DOUBLE
  //                                 | STR_SQ
  //                                 | STR_DQ
  //                                 | 'true'
  //                                 | 'false'
  //                                 | 'null'
  static boolean simple_literal_tokens(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_literal_tokens")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, NUM_INT);
    if (!r) r = consumeTokenFast(b, NUM_LONG);
    if (!r) r = consumeTokenFast(b, NUM_BIG_INT);
    if (!r) r = consumeTokenFast(b, NUM_BIG_DECIMAL);
    if (!r) r = consumeTokenFast(b, NUM_FLOAT);
    if (!r) r = consumeTokenFast(b, NUM_DOUBLE);
    if (!r) r = consumeTokenFast(b, STR_SQ);
    if (!r) r = consumeTokenFast(b, STR_DQ);
    if (!r) r = consumeTokenFast(b, KW_TRUE);
    if (!r) r = consumeTokenFast(b, KW_FALSE);
    if (!r) r = consumeTokenFast(b, KW_NULL);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // short_simple_reference simple_reference_tail*
  public static boolean simple_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_reference")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, CODE_REFERENCE, "<simple reference>");
    r = short_simple_reference(b, l + 1);
    r = r && simple_reference_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // simple_reference_tail*
  private static boolean simple_reference_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_reference_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!simple_reference_tail(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "simple_reference_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // code_reference_dot code_reference_identifier
  public static boolean simple_reference_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_reference_tail")) return false;
    if (!nextTokenIs(b, T_DOT)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _LEFT_, CODE_REFERENCE, null);
    r = code_reference_dot(b, l + 1);
    r = r && code_reference_identifier(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '(' unqualified_reference_expression ')'
  public static boolean single_tuple(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "single_tuple")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_LPAREN);
    r = r && unqualified_reference_expression(b, l + 1);
    r = r && consumeToken(b, T_RPAREN);
    exit_section_(b, m, TUPLE, r);
    return r;
  }

  /* ********************************************************** */
  // single_tuple tuple_initializer
  static boolean single_tuple_assignment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "single_tuple_assignment")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = single_tuple(b, l + 1);
    r = r && tuple_initializer(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // SLASHY_BEGIN fast_slashy_content !'$' SLASHY_END
  public static boolean slashy_literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "slashy_literal")) return false;
    if (!nextTokenIs(b, SLASHY_BEGIN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, SLASHY_LITERAL, null);
    r = consumeToken(b, SLASHY_BEGIN);
    r = r && fast_slashy_content(b, l + 1);
    r = r && slashy_literal_2(b, l + 1);
    p = r; // pin = 3
    r = r && consumeToken(b, SLASHY_END);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // !'$'
  private static boolean slashy_literal_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "slashy_literal_2")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, T_DOLLAR);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<compound_string SLASHY_BEGIN fast_slashy_content SLASHY_END>>
  static boolean slashy_string(PsiBuilder b, int l) {
    return compound_string(b, l + 1, SLASHY_BEGIN_parser_, fast_slashy_content_parser_, SLASHY_END_parser_);
  }

  /* ********************************************************** */
  // '<' soft_type_argument_list_item soft_type_argument_list_tail* type_argument_list_end
  public static boolean soft_type_argument_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "soft_type_argument_list")) return false;
    if (!nextTokenIsFast(b, T_LT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_LT);
    r = r && soft_type_argument_list_item(b, l + 1);
    r = r && soft_type_argument_list_2(b, l + 1);
    r = r && type_argument_list_end(b, l + 1);
    exit_section_(b, m, TYPE_ARGUMENT_LIST, r);
    return r;
  }

  // soft_type_argument_list_tail*
  private static boolean soft_type_argument_list_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "soft_type_argument_list_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!soft_type_argument_list_tail(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "soft_type_argument_list_2", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // type_argument_list_item | clear_variants_and_fail
  static boolean soft_type_argument_list_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "soft_type_argument_list_item")) return false;
    boolean r;
    r = type_argument_list_item(b, l + 1);
    if (!r) r = clear_variants_and_fail(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // fast_comma mb_nl soft_type_argument_list_item
  static boolean soft_type_argument_list_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "soft_type_argument_list_tail")) return false;
    if (!nextTokenIs(b, T_COMMA)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fast_comma(b, l + 1);
    r = r && mb_nl(b, l + 1);
    r = r && soft_type_argument_list_item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // !<<isApplicationArguments>> '*' expression
  public static boolean spread_list_argument(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "spread_list_argument")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SPREAD_LIST_ARGUMENT, "<spread list argument>");
    r = spread_list_argument_0(b, l + 1);
    r = r && consumeTokenFast(b, T_STAR);
    r = r && expression(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // !<<isApplicationArguments>>
  private static boolean spread_list_argument_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "spread_list_argument_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !isApplicationArguments(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // if_statement
  //             | switch_statement
  //             | try_statement
  //             | while_statement
  //             | do_while_statement
  //             | for_statement
  //             | synchronized_statement
  //             | return_statement
  //             | break_statement
  //             | continue_statement
  //             | assert_statement
  //             | throw_statement
  //             | labeled_statement
  //             | type_definition | <<withProtectedLastVariantPos tuple_var_declaration>> | parse_block_declaration
  //             | expression_or_application
  public static boolean statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<statement>");
    r = if_statement(b, l + 1);
    if (!r) r = switch_statement(b, l + 1);
    if (!r) r = try_statement(b, l + 1);
    if (!r) r = while_statement(b, l + 1);
    if (!r) r = do_while_statement(b, l + 1);
    if (!r) r = for_statement(b, l + 1);
    if (!r) r = synchronized_statement(b, l + 1);
    if (!r) r = return_statement(b, l + 1);
    if (!r) r = break_statement(b, l + 1);
    if (!r) r = continue_statement(b, l + 1);
    if (!r) r = assert_statement(b, l + 1);
    if (!r) r = throw_statement(b, l + 1);
    if (!r) r = labeled_statement(b, l + 1);
    if (!r) r = type_definition(b, l + 1);
    if (!r) r = withProtectedLastVariantPos(b, l + 1, tuple_var_declaration_parser_);
    if (!r) r = parse_block_declaration(b, l + 1);
    if (!r) r = expression_or_application(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'assert'
  //                              | 'break'
  //                              | 'continue'
  //                              | 'for'
  //                              | 'if'
  //                              | 'return'
  //                              | 'switch'
  //                              | 'throw'
  //                              | 'try'
  //                              | 'while'
  static boolean statement_keywords(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_keywords")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, KW_ASSERT);
    if (!r) r = consumeTokenFast(b, KW_BREAK);
    if (!r) r = consumeTokenFast(b, KW_CONTINUE);
    if (!r) r = consumeTokenFast(b, KW_FOR);
    if (!r) r = consumeTokenFast(b, KW_IF);
    if (!r) r = consumeTokenFast(b, KW_RETURN);
    if (!r) r = consumeTokenFast(b, KW_SWITCH);
    if (!r) r = consumeTokenFast(b, KW_THROW);
    if (!r) r = consumeTokenFast(b, KW_TRY);
    if (!r) r = consumeTokenFast(b, KW_WHILE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // expression_start
  //                           | '@'
  //                           | statement_keywords
  //                           | modifier
  //                           | primitive_type
  static boolean statement_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_start")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expression_start(b, l + 1);
    if (!r) r = consumeToken(b, T_AT);
    if (!r) r = statement_keywords(b, l + 1);
    if (!r) r = modifier(b, l + 1);
    if (!r) r = parsePrimitiveType(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<content>>
  public static boolean string_content(PsiBuilder b, int l, Parser _content) {
    if (!recursion_guard_(b, l, "string_content")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _content.parse(b, l);
    exit_section_(b, m, STRING_CONTENT, r);
    return r;
  }

  /* ********************************************************** */
  // '$' string_injection_body
  public static boolean string_injection(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_injection")) return false;
    if (!nextTokenIsFast(b, T_DOLLAR)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, STRING_INJECTION, null);
    r = consumeTokenFast(b, T_DOLLAR);
    p = r; // pin = 1
    r = r && string_injection_body(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // qualified_reference_expression | unqualified_reference_expression | lazy_closure | clear_variants <<unexpected "identifier.or.block.expected">>
  static boolean string_injection_body(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_injection_body")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expression(b, l + 1, 15);
    if (!r) r = unqualified_reference_expression(b, l + 1);
    if (!r) r = lazy_closure(b, l + 1);
    if (!r) r = string_injection_body_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // clear_variants <<unexpected "identifier.or.block.expected">>
  private static boolean string_injection_body_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_injection_body_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = clearVariants(b, l + 1);
    r = r && unexpected(b, l + 1, "identifier.or.block.expected");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '('                                           // explicit method call
  //                                       | !<<isArguments>> !<<isApplicationArguments>> application_argument_start   // followed by application arguments
  //                                       | <<closureArgumentSeparator '{'>>
  static boolean string_literal_as_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_literal_as_reference")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_LPAREN);
    if (!r) r = string_literal_as_reference_1(b, l + 1);
    if (!r) r = closureArgumentSeparator(b, l + 1, T_LBRACE_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  // !<<isArguments>> !<<isApplicationArguments>> application_argument_start
  private static boolean string_literal_as_reference_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_literal_as_reference_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = string_literal_as_reference_1_0(b, l + 1);
    r = r && string_literal_as_reference_1_1(b, l + 1);
    r = r && application_argument_start(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // !<<isArguments>>
  private static boolean string_literal_as_reference_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_literal_as_reference_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !isArguments(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // !<<isApplicationArguments>>
  private static boolean string_literal_as_reference_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_literal_as_reference_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !isApplicationArguments(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '{' mb_nl case_sections* '}'
  static boolean switch_body(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "switch_body")) return false;
    if (!nextTokenIs(b, T_LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && report_error_(b, switch_body_2(b, l + 1)) && r;
    r = p && consumeToken(b, T_RBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // case_sections*
  private static boolean switch_body_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "switch_body_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!case_sections(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "switch_body_2", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // 'switch' '(' expression ')' (mb_nl switch_body)
  public static boolean switch_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "switch_statement")) return false;
    if (!nextTokenIs(b, KW_SWITCH)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, SWITCH_STATEMENT, null);
    r = consumeTokens(b, 1, KW_SWITCH, T_LPAREN);
    p = r; // pin = 1
    r = r && report_error_(b, expression(b, l + 1, -1));
    r = p && report_error_(b, consumeToken(b, T_RPAREN)) && r;
    r = p && switch_statement_4(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // mb_nl switch_body
  private static boolean switch_statement_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "switch_statement_4")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && switch_body(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'synchronized' '(' expression ')' lazy_block
  public static boolean synchronized_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "synchronized_statement")) return false;
    if (!nextTokenIs(b, KW_SYNCHRONIZED)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, SYNCHRONIZED_STATEMENT, null);
    r = consumeTokens(b, 2, KW_SYNCHRONIZED, T_LPAREN);
    p = r; // pin = 2
    r = r && report_error_(b, expression(b, l + 1, -1));
    r = p && report_error_(b, consumeToken(b, T_RPAREN)) && r;
    r = p && lazy_block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '?' mb_nl expression mb_nl ':' mb_nl conditionals
  static boolean ternary_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ternary_tail")) return false;
    if (!nextTokenIsFast(b, T_Q)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokenFast(b, T_Q);
    r = r && mb_nl(b, l + 1);
    r = r && expression(b, l + 1, -1);
    r = r && mb_nl(b, l + 1);
    r = r && consumeToken(b, T_COLON);
    p = r; // pin = 5
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && expression(b, l + 1, 0) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '?' mb_nl !'[' expression mb_nl ':' mb_nl conditionals
  static boolean ternary_tail_pin(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ternary_tail_pin")) return false;
    if (!nextTokenIsFast(b, T_Q)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokenFast(b, T_Q);
    r = r && mb_nl(b, l + 1);
    r = r && ternary_tail_pin_2(b, l + 1);
    p = r; // pin = 3
    r = r && report_error_(b, expression(b, l + 1, -1));
    r = p && report_error_(b, mb_nl(b, l + 1)) && r;
    r = p && report_error_(b, consumeToken(b, T_COLON)) && r;
    r = p && report_error_(b, mb_nl(b, l + 1)) && r;
    r = p && expression(b, l + 1, 0) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // !'['
  private static boolean ternary_tail_pin_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ternary_tail_pin_2")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeTokenFast(b, T_LBRACK);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'throw' expression
  public static boolean throw_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "throw_statement")) return false;
    if (!nextTokenIs(b, KW_THROW)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, THROW_STATEMENT, null);
    r = consumeToken(b, KW_THROW);
    p = r; // pin = 1
    r = r && expression(b, l + 1, -1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // mb_nl type_code_reference
  static boolean throws_list_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "throws_list_item")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && type_code_reference(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // import | statement
  static boolean top_level(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "top_level")) return false;
    boolean r;
    r = import_$(b, l + 1);
    if (!r) r = statement(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // separators | <<eof>>
  static boolean top_level_end(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "top_level_end")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = separators(b, l + 1);
    if (!r) r = eof(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<addVariant "statement">> <<separated_item top_level_end top_level top_level_start>>
  static boolean top_level_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "top_level_item")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = addVariant(b, l + 1, "statement");
    r = r && separated_item(b, l + 1, top_level_end_parser_, top_level_parser_, top_level_start_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'import' | block_level_start
  static boolean top_level_start(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "top_level_start")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_IMPORT);
    if (!r) r = block_level_start(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // top_level_item* clear_error
  static boolean top_levels(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "top_levels")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = top_levels_0(b, l + 1);
    r = r && clearError(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // top_level_item*
  private static boolean top_levels_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "top_levels_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!top_level_item(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "top_levels_0", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // empty for_clause_initialization? ';' expression? ';' mb_nl expression?
  public static boolean traditional_for_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "traditional_for_clause")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, TRADITIONAL_FOR_CLAUSE, "<traditional for clause>");
    r = empty(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, traditional_for_clause_1(b, l + 1));
    r = p && report_error_(b, consumeToken(b, T_SEMI)) && r;
    r = p && report_error_(b, traditional_for_clause_3(b, l + 1)) && r;
    r = p && report_error_(b, consumeToken(b, T_SEMI)) && r;
    r = p && report_error_(b, mb_nl(b, l + 1)) && r;
    r = p && traditional_for_clause_6(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // for_clause_initialization?
  private static boolean traditional_for_clause_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "traditional_for_clause_1")) return false;
    for_clause_initialization(b, l + 1);
    return true;
  }

  // expression?
  private static boolean traditional_for_clause_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "traditional_for_clause_3")) return false;
    expression(b, l + 1, -1);
    return true;
  }

  // expression?
  private static boolean traditional_for_clause_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "traditional_for_clause_6")) return false;
    expression(b, l + 1, -1);
    return true;
  }

  /* ********************************************************** */
  // traditional_for_parameter_inner mb_initializer
  public static boolean traditional_for_parameter(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "traditional_for_parameter")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PARAMETER, "<traditional for parameter>");
    r = traditional_for_parameter_inner(b, l + 1);
    r = r && mb_initializer(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // non_empty_modifier_list mb_type_element IDENTIFIER
  //                                           | empty_modifier_list type_element IDENTIFIER
  static boolean traditional_for_parameter_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "traditional_for_parameter_inner")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = traditional_for_parameter_inner_0(b, l + 1);
    if (!r) r = traditional_for_parameter_inner_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // non_empty_modifier_list mb_type_element IDENTIFIER
  private static boolean traditional_for_parameter_inner_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "traditional_for_parameter_inner_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = non_empty_modifier_list(b, l + 1);
    r = r && mb_type_element(b, l + 1);
    r = r && consumeToken(b, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  // empty_modifier_list type_element IDENTIFIER
  private static boolean traditional_for_parameter_inner_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "traditional_for_parameter_inner_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = empty_modifier_list(b, l + 1);
    r = r && type_element(b, l + 1);
    r = r && consumeToken(b, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER type_parameter_list? nl_extends nl_implements
  static boolean trait_definition_header(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_definition_header")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && trait_definition_header_1(b, l + 1);
    r = r && nl_extends(b, l + 1);
    r = r && nl_implements(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_parameter_list?
  private static boolean trait_definition_header_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_definition_header_1")) return false;
    type_parameter_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // 'trait' trait_definition_header mb_nl class_body
  public static boolean trait_type_definition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_type_definition")) return false;
    if (!nextTokenIsFast(b, KW_TRAIT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _LEFT_, TRAIT_TYPE_DEFINITION, null);
    r = consumeTokenFast(b, KW_TRAIT);
    r = r && trait_definition_header(b, l + 1);
    p = r; // pin = trait_definition_header
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && class_body(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '(' try_resource_list_item+ ')'
  public static boolean try_resource_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "try_resource_list")) return false;
    if (!nextTokenIs(b, T_LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, TRY_RESOURCE_LIST, null);
    r = consumeToken(b, T_LPAREN);
    p = r; // pin = 1
    r = r && report_error_(b, try_resource_list_1(b, l + 1));
    r = p && consumeToken(b, T_RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // try_resource_list_item+
  private static boolean try_resource_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "try_resource_list_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = try_resource_list_item(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!try_resource_list_item(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "try_resource_list_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // local_variable_declaration try_resource_list_separator
  static boolean try_resource_list_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "try_resource_list_item")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = local_variable_declaration(b, l + 1);
    p = r; // pin = 1
    r = r && try_resource_list_separator(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // &')' | separators | <<newLine>>
  static boolean try_resource_list_separator(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "try_resource_list_separator")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = try_resource_list_separator_0(b, l + 1);
    if (!r) r = separators(b, l + 1);
    if (!r) r = newLine(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &')'
  private static boolean try_resource_list_separator_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "try_resource_list_separator_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, T_RPAREN);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'try' try_resource_list? mb_nl lazy_block (mb_nl catch_clause)* [mb_nl finally_clause]
  public static boolean try_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "try_statement")) return false;
    if (!nextTokenIs(b, KW_TRY)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, TRY_STATEMENT, null);
    r = consumeToken(b, KW_TRY);
    p = r; // pin = 1
    r = r && report_error_(b, try_statement_1(b, l + 1));
    r = p && report_error_(b, mb_nl(b, l + 1)) && r;
    r = p && report_error_(b, lazy_block(b, l + 1)) && r;
    r = p && report_error_(b, try_statement_4(b, l + 1)) && r;
    r = p && try_statement_5(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // try_resource_list?
  private static boolean try_statement_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "try_statement_1")) return false;
    try_resource_list(b, l + 1);
    return true;
  }

  // (mb_nl catch_clause)*
  private static boolean try_statement_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "try_statement_4")) return false;
    while (true) {
      int c = current_position_(b);
      if (!try_statement_4_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "try_statement_4", c)) break;
    }
    return true;
  }

  // mb_nl catch_clause
  private static boolean try_statement_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "try_statement_4_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && catch_clause(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [mb_nl finally_clause]
  private static boolean try_statement_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "try_statement_5")) return false;
    try_statement_5_0(b, l + 1);
    return true;
  }

  // mb_nl finally_clause
  private static boolean try_statement_5_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "try_statement_5_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && finally_clause(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '(' unqualified_reference_expression (',' unqualified_reference_expression)+ ')'
  public static boolean tuple(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple")) return false;
    if (!nextTokenIsFast(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_LPAREN);
    r = r && unqualified_reference_expression(b, l + 1);
    r = r && tuple_2(b, l + 1);
    r = r && consumeToken(b, T_RPAREN);
    exit_section_(b, m, TUPLE, r);
    return r;
  }

  // (',' unqualified_reference_expression)+
  private static boolean tuple_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = tuple_2_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!tuple_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "tuple_2", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // ',' unqualified_reference_expression
  private static boolean tuple_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_COMMA);
    r = r && unqualified_reference_expression(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '=' mb_nl expression
  static boolean tuple_initializer(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_initializer")) return false;
    if (!nextTokenIs(b, T_ASSIGN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_ASSIGN);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && expression(b, l + 1, -1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // non_empty_modifier_list tuple_var_declaration_tuple tuple_initializer
  public static boolean tuple_var_declaration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_var_declaration")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, VARIABLE_DECLARATION, "<tuple var declaration>");
    r = non_empty_modifier_list(b, l + 1);
    r = r && tuple_var_declaration_tuple(b, l + 1);
    p = r; // pin = tuple_var_declaration_tuple
    r = r && tuple_initializer(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // type_element tuple_variable | tuple_variable
  static boolean tuple_var_declaration_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_var_declaration_item")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = tuple_var_declaration_item_0(b, l + 1);
    if (!r) r = tuple_variable(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_element tuple_variable
  private static boolean tuple_var_declaration_item_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_var_declaration_item_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_element(b, l + 1);
    r = r && tuple_variable(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '(' tuple_var_declaration_item (',' tuple_var_declaration_item)* ')'
  static boolean tuple_var_declaration_tuple(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_var_declaration_tuple")) return false;
    if (!nextTokenIsFast(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_LPAREN);
    r = r && tuple_var_declaration_item(b, l + 1);
    r = r && tuple_var_declaration_tuple_2(b, l + 1);
    r = r && consumeToken(b, T_RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // (',' tuple_var_declaration_item)*
  private static boolean tuple_var_declaration_tuple_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_var_declaration_tuple_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!tuple_var_declaration_tuple_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "tuple_var_declaration_tuple_2", c)) break;
    }
    return true;
  }

  // ',' tuple_var_declaration_item
  private static boolean tuple_var_declaration_tuple_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_var_declaration_tuple_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, T_COMMA);
    r = r && tuple_var_declaration_item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean tuple_variable(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_variable")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, VARIABLE, r);
    return r;
  }

  /* ********************************************************** */
  // any_type_element | wildcard_type_element
  static boolean type_argument(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_argument")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<type argument>");
    r = any_type_element(b, l + 1);
    if (!r) r = wildcard_type_element(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // diamond_type_argument_list | non_empty_type_argument_list
  public static boolean type_argument_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_argument_list")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TYPE_ARGUMENT_LIST, "<type argument list>");
    r = diamond_type_argument_list(b, l + 1);
    if (!r) r = non_empty_type_argument_list(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<mb_nl_group '>'>>
  static boolean type_argument_list_end(PsiBuilder b, int l) {
    return mb_nl_group(b, l + 1, T_GT_parser_);
  }

  /* ********************************************************** */
  // mb_nl type_argument
  static boolean type_argument_list_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_argument_list_item")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && type_argument(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // code_reference
  public static boolean type_code_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_code_reference")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<type>");
    r = code_reference(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // (non_empty_modifier_list mb_nl | empty_modifier_list) type_definition_tail
  static boolean type_definition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_definition")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_definition_0(b, l + 1);
    r = r && type_definition_tail(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // non_empty_modifier_list mb_nl | empty_modifier_list
  private static boolean type_definition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_definition_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_definition_0_0(b, l + 1);
    if (!r) r = empty_modifier_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // non_empty_modifier_list mb_nl
  private static boolean type_definition_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_definition_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = non_empty_modifier_list(b, l + 1);
    r = r && mb_nl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // class_type_definition
  //                                | interface_type_definition
  //                                | trait_type_definition
  //                                | enum_type_definition
  //                                | annotation_type_definition
  static boolean type_definition_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_definition_tail")) return false;
    boolean r;
    r = class_type_definition(b, l + 1);
    if (!r) r = interface_type_definition(b, l + 1);
    if (!r) r = trait_type_definition(b, l + 1);
    if (!r) r = enum_type_definition(b, l + 1);
    if (!r) r = annotation_type_definition(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // (primitive_type_element | class_type_element) array_type_elements
  public static boolean type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, TYPE_ELEMENT, "<type>");
    r = type_element_0(b, l + 1);
    r = r && array_type_elements(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // primitive_type_element | class_type_element
  private static boolean type_element_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_element_0")) return false;
    boolean r;
    r = primitive_type_element(b, l + 1);
    if (!r) r = class_type_element(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // type_element &IDENTIFIER
  static boolean type_element_followed_by_identifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_element_followed_by_identifier")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_element(b, l + 1);
    r = r && type_element_followed_by_identifier_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &IDENTIFIER
  private static boolean type_element_followed_by_identifier_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_element_followed_by_identifier_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER (type_parameter_bounds_list | empty_type_parameter_bounds_list)
  public static boolean type_parameter(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_parameter")) return false;
    if (!nextTokenIs(b, "<type parameter>", IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TYPE_PARAMETER, "<type parameter>");
    r = consumeToken(b, IDENTIFIER);
    r = r && type_parameter_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // type_parameter_bounds_list | empty_type_parameter_bounds_list
  private static boolean type_parameter_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_parameter_1")) return false;
    boolean r;
    r = type_parameter_bounds_list(b, l + 1);
    if (!r) r = empty_type_parameter_bounds_list(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // type_code_reference type_parameter_bounds_tail*
  static boolean type_parameter_bounds(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_parameter_bounds")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_code_reference(b, l + 1);
    r = r && type_parameter_bounds_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_parameter_bounds_tail*
  private static boolean type_parameter_bounds_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_parameter_bounds_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!type_parameter_bounds_tail(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "type_parameter_bounds_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // 'extends' type_parameter_bounds
  public static boolean type_parameter_bounds_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_parameter_bounds_list")) return false;
    if (!nextTokenIs(b, KW_EXTENDS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, TYPE_PARAMETER_BOUNDS_LIST, null);
    r = consumeToken(b, KW_EXTENDS);
    p = r; // pin = 1
    r = r && type_parameter_bounds(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '&' type_code_reference
  static boolean type_parameter_bounds_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_parameter_bounds_tail")) return false;
    if (!nextTokenIs(b, T_BAND)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_BAND);
    p = r; // pin = 1
    r = r && type_code_reference(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '<' type_parameters '>'
  public static boolean type_parameter_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_parameter_list")) return false;
    if (!nextTokenIsFast(b, T_LT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, TYPE_PARAMETER_LIST, null);
    r = consumeTokenFast(b, T_LT);
    p = r; // pin = 1
    r = r && report_error_(b, type_parameters(b, l + 1));
    r = p && consumeToken(b, T_GT) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // type_parameter type_parameters_tail*
  static boolean type_parameters(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_parameters")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_parameter(b, l + 1);
    r = r && type_parameters_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_parameters_tail*
  private static boolean type_parameters_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_parameters_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!type_parameters_tail(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "type_parameters_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // ',' type_parameter
  static boolean type_parameters_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_parameters_tail")) return false;
    if (!nextTokenIs(b, T_COMMA)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_COMMA);
    p = r; // pin = 1
    r = r && type_parameter(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // short_code_reference
  public static boolean unqualified_class_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unqualified_class_type_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CLASS_TYPE_ELEMENT, "<type>");
    r = short_code_reference(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER mb_initializer
  static boolean var(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var")) return false;
    if (!nextTokenIsFast(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenFast(b, IDENTIFIER);
    r = r && mb_initializer(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // var
  public static boolean variable(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "variable")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = var(b, l + 1);
    exit_section_(b, m, VARIABLE, r);
    return r;
  }

  /* ********************************************************** */
  // variable (',' mb_nl variable)*
  public static boolean variable_declaration_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "variable_declaration_tail")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = variable(b, l + 1);
    r = r && variable_declaration_tail_1(b, l + 1);
    exit_section_(b, m, VARIABLE_DECLARATION, r);
    return r;
  }

  // (',' mb_nl variable)*
  private static boolean variable_declaration_tail_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "variable_declaration_tail_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!variable_declaration_tail_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "variable_declaration_tail_1", c)) break;
    }
    return true;
  }

  // ',' mb_nl variable
  private static boolean variable_declaration_tail_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "variable_declaration_tail_1_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_COMMA);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && variable(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // 'while' ('(' mb_nl expression mb_nl ')' mb_nl branch)
  public static boolean while_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "while_statement")) return false;
    if (!nextTokenIs(b, KW_WHILE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, WHILE_STATEMENT, null);
    r = consumeToken(b, KW_WHILE);
    p = r; // pin = 1
    r = r && while_statement_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '(' mb_nl expression mb_nl ')' mb_nl branch
  private static boolean while_statement_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "while_statement_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, T_LPAREN);
    p = r; // pin = 1
    r = r && report_error_(b, mb_nl(b, l + 1));
    r = p && report_error_(b, expression(b, l + 1, -1)) && r;
    r = p && report_error_(b, mb_nl(b, l + 1)) && r;
    r = p && report_error_(b, consumeToken(b, T_RPAREN)) && r;
    r = p && report_error_(b, mb_nl(b, l + 1)) && r;
    r = p && branch(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // ('extends' | 'super') any_type_element
  static boolean wildcard_bound(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "wildcard_bound")) return false;
    if (!nextTokenIs(b, "", KW_EXTENDS, KW_SUPER)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = wildcard_bound_0(b, l + 1);
    p = r; // pin = 1
    r = r && any_type_element(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // 'extends' | 'super'
  private static boolean wildcard_bound_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "wildcard_bound_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_EXTENDS);
    if (!r) r = consumeToken(b, KW_SUPER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '?' wildcard_bound?
  public static boolean wildcard_type_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "wildcard_type_element")) return false;
    if (!nextTokenIs(b, "<type>", T_Q)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, WILDCARD_TYPE_ELEMENT, "<type>");
    r = consumeToken(b, T_Q);
    r = r && wildcard_type_element_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // wildcard_bound?
  private static boolean wildcard_type_element_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "wildcard_type_element_1")) return false;
    wildcard_bound(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // Expression root: expression
  // Operator priority table:
  // 0: BINARY(assignment_expression) ATOM(tuple_assignment_expression)
  // 1: POSTFIX(ternary_expression) BINARY(elvis_expression)
  // 2: BINARY(lor_expression)
  // 3: BINARY(land_expression)
  // 4: BINARY(bor_expression)
  // 5: BINARY(xor_expression)
  // 6: BINARY(band_expression)
  // 7: BINARY(equality_expression) BINARY(compare_expression) BINARY(regex_find_expression) BINARY(regex_match_expression)
  // 8: BINARY(relational_expression) BINARY(in_expression) POSTFIX(instanceof_expression) POSTFIX(as_expression)
  // 9: BINARY(shift_expression) BINARY(range_expression)
  // 10: BINARY(additive_expression)
  // 11: BINARY(multiplicative_expression)
  // 12: BINARY(power_expression)
  // 13: PREFIX(prefix_unary_expression)
  // 14: PREFIX(not_expression) ATOM(cast_expression)
  // 15: POSTFIX(index_expression) POSTFIX(safe_index_expression) POSTFIX(postfix_unary_expression)
  // 16: POSTFIX(method_reference_expression) POSTFIX(qualified_reference_expression) POSTFIX(property_expression)
  // 17: POSTFIX(method_call_expression) ATOM(lazy_closure) ATOM(list_or_map)
  // 18: ATOM(new_anonymous_expression) ATOM(new_expression)
  // 19: ATOM(unqualified_reference_expression) ATOM(built_in_type_expression) ATOM(literal) ATOM(gstring)
  //    ATOM(regex) ATOM(parenthesized_expression)
  public static boolean expression(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expression")) return false;
    addVariant(b, "<expression>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<expression>");
    r = tuple_assignment_expression(b, l + 1);
    if (!r) r = prefix_unary_expression(b, l + 1);
    if (!r) r = not_expression(b, l + 1);
    if (!r) r = cast_expression(b, l + 1);
    if (!r) r = lazy_closure(b, l + 1);
    if (!r) r = list_or_map(b, l + 1);
    if (!r) r = new_anonymous_expression(b, l + 1);
    if (!r) r = new_expression(b, l + 1);
    if (!r) r = unqualified_reference_expression(b, l + 1);
    if (!r) r = built_in_type_expression(b, l + 1);
    if (!r) r = literal(b, l + 1);
    if (!r) r = gstring(b, l + 1);
    if (!r) r = regex(b, l + 1);
    if (!r) r = parenthesized_expression(b, l + 1);
    p = r;
    r = r && expression_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean expression_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expression_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 0 && assignment_expression_0(b, l + 1)) {
        r = report_error_(b, expression(b, l, -1));
        r = assignment_expression_1(b, l + 1) && r;
        exit_section_(b, l, m, ASSIGNMENT_EXPRESSION, r, true, null);
      }
      else if (g < 1 && ternary_expression_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, TERNARY_EXPRESSION, r, true, null);
      }
      else if (g < 1 && elvis_expression_0(b, l + 1)) {
        r = expression(b, l, 0);
        exit_section_(b, l, m, ELVIS_EXPRESSION, r, true, null);
      }
      else if (g < 2 && lor_expression_0(b, l + 1)) {
        r = expression(b, l, 2);
        exit_section_(b, l, m, LOR_EXPRESSION, r, true, null);
      }
      else if (g < 3 && land_expression_0(b, l + 1)) {
        r = expression(b, l, 3);
        exit_section_(b, l, m, LAND_EXPRESSION, r, true, null);
      }
      else if (g < 4 && consumeTokenSmart(b, T_BOR)) {
        r = expression(b, l, 4);
        exit_section_(b, l, m, BOR_EXPRESSION, r, true, null);
      }
      else if (g < 5 && consumeTokenSmart(b, T_XOR)) {
        r = expression(b, l, 5);
        exit_section_(b, l, m, XOR_EXPRESSION, r, true, null);
      }
      else if (g < 6 && consumeTokenSmart(b, T_BAND)) {
        r = expression(b, l, 6);
        exit_section_(b, l, m, BAND_EXPRESSION, r, true, null);
      }
      else if (g < 7 && equalityOperator(b, l + 1)) {
        r = expression(b, l, 7);
        exit_section_(b, l, m, EQUALITY_EXPRESSION, r, true, null);
      }
      else if (g < 7 && compare_expression_0(b, l + 1)) {
        r = expression(b, l, 7);
        exit_section_(b, l, m, RELATIONAL_EXPRESSION, r, true, null);
      }
      else if (g < 7 && consumeTokenSmart(b, T_REGEX_FIND)) {
        r = expression(b, l, 7);
        exit_section_(b, l, m, REGEX_FIND_EXPRESSION, r, true, null);
      }
      else if (g < 7 && consumeTokenSmart(b, T_REGEX_MATCH)) {
        r = expression(b, l, 7);
        exit_section_(b, l, m, REGEX_MATCH_EXPRESSION, r, true, null);
      }
      else if (g < 8 && relational_expression_0(b, l + 1)) {
        r = expression(b, l, 8);
        exit_section_(b, l, m, RELATIONAL_EXPRESSION, r, true, null);
      }
      else if (g < 8 && in_expression_0(b, l + 1)) {
        r = expression(b, l, 8);
        exit_section_(b, l, m, IN_EXPRESSION, r, true, null);
      }
      else if (g < 8 && instanceof_expression_tail(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, INSTANCEOF_EXPRESSION, r, true, null);
      }
      else if (g < 8 && as_expression_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, AS_EXPRESSION, r, true, null);
      }
      else if (g < 9 && shift_sign(b, l + 1)) {
        r = expression(b, l, 9);
        exit_section_(b, l, m, SHIFT_EXPRESSION, r, true, null);
      }
      else if (g < 9 && range_expression_0(b, l + 1)) {
        r = expression(b, l, 9);
        exit_section_(b, l, m, RANGE_EXPRESSION, r, true, null);
      }
      else if (g < 10 && additive_expression_0(b, l + 1)) {
        r = expression(b, l, 10);
        exit_section_(b, l, m, ADDITIVE_EXPRESSION, r, true, null);
      }
      else if (g < 11 && multiplicative_expression_0(b, l + 1)) {
        r = expression(b, l, 11);
        exit_section_(b, l, m, MULTIPLICATIVE_EXPRESSION, r, true, null);
      }
      else if (g < 12 && power_expression_0(b, l + 1)) {
        r = expression(b, l, 12);
        exit_section_(b, l, m, POWER_EXPRESSION, r, true, null);
      }
      else if (g < 15 && index_expression_argument_list(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, INDEX_EXPRESSION, r, true, null);
      }
      else if (g < 15 && safe_index_expression_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, INDEX_EXPRESSION, r, true, null);
      }
      else if (g < 15 && postfix_unary_expression_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, UNARY_EXPRESSION, r, true, null);
      }
      else if (g < 16 && method_reference_expression_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, METHOD_REFERENCE_EXPRESSION, r, true, null);
      }
      else if (g < 16 && qualified_reference_expression_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, REFERENCE_EXPRESSION, r, true, null);
      }
      else if (g < 16 && property_expression_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, PROPERTY_EXPRESSION, r, true, null);
      }
      else if (g < 17 && call_tail(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, METHOD_CALL_EXPRESSION, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // <<assignmentOperator>> mb_nl
  private static boolean assignment_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = assignmentOperator(b, l + 1);
    r = r && mb_nl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // application*
  private static boolean assignment_expression_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_expression_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!application(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "assignment_expression_1", c)) break;
    }
    return true;
  }

  // single_tuple_assignment | multi_tuple_assignment
  public static boolean tuple_assignment_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_assignment_expression")) return false;
    if (!nextTokenIsSmart(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = single_tuple_assignment(b, l + 1);
    if (!r) r = multi_tuple_assignment(b, l + 1);
    exit_section_(b, m, TUPLE_ASSIGNMENT_EXPRESSION, r);
    return r;
  }

  // ternary_tail_pin | ternary_tail
  private static boolean ternary_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ternary_expression_0")) return false;
    boolean r;
    r = ternary_tail_pin(b, l + 1);
    if (!r) r = ternary_tail(b, l + 1);
    return r;
  }

  // '?:' mb_nl
  private static boolean elvis_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "elvis_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, T_ELVIS);
    r = r && mb_nl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '||' mb_nl
  private static boolean lor_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lor_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, T_LOR);
    r = r && mb_nl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '&&' mb_nl
  private static boolean land_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "land_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, T_LAND);
    r = r && mb_nl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<=>' mb_nl
  private static boolean compare_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compare_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, T_COMPARE);
    r = r && mb_nl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // relational_operator mb_nl
  private static boolean relational_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relational_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = relational_operator(b, l + 1);
    r = r && mb_nl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // mb_not ('in')
  private static boolean in_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "in_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_not(b, l + 1);
    r = r && in_expression_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ('in')
  private static boolean in_expression_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "in_expression_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, KW_IN);
    exit_section_(b, m, null, r);
    return r;
  }

  // 'as' type_element
  private static boolean as_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "as_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, KW_AS);
    r = r && type_element(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '..' | '..<'
  private static boolean range_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "range_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, T_RANGE);
    if (!r) r = consumeTokenSmart(b, T_RANGE_EX);
    exit_section_(b, m, null, r);
    return r;
  }

  // ('+' | '-') mb_nl
  private static boolean additive_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "additive_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = additive_expression_0_0(b, l + 1);
    r = r && mb_nl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '+' | '-'
  private static boolean additive_expression_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "additive_expression_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, T_PLUS);
    if (!r) r = consumeTokenSmart(b, T_MINUS);
    exit_section_(b, m, null, r);
    return r;
  }

  // ('*' | '/' | '%') mb_nl
  private static boolean multiplicative_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "multiplicative_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = multiplicative_expression_0_0(b, l + 1);
    r = r && mb_nl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '*' | '/' | '%'
  private static boolean multiplicative_expression_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "multiplicative_expression_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, T_STAR);
    if (!r) r = consumeTokenSmart(b, T_DIV);
    if (!r) r = consumeTokenSmart(b, T_REM);
    exit_section_(b, m, null, r);
    return r;
  }

  // '**' mb_nl
  private static boolean power_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "power_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, T_POW);
    r = r && mb_nl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  public static boolean prefix_unary_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "prefix_unary_expression")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = prefix_unary_expression_0(b, l + 1);
    p = r;
    r = p && expression(b, l, 13);
    exit_section_(b, l, m, UNARY_EXPRESSION, r, p, null);
    return r || p;
  }

  // ('++' | '--' | '-' | '+') mb_nl
  private static boolean prefix_unary_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "prefix_unary_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = prefix_unary_expression_0_0(b, l + 1);
    r = r && mb_nl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '++' | '--' | '-' | '+'
  private static boolean prefix_unary_expression_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "prefix_unary_expression_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, T_INC);
    if (!r) r = consumeTokenSmart(b, T_DEC);
    if (!r) r = consumeTokenSmart(b, T_MINUS);
    if (!r) r = consumeTokenSmart(b, T_PLUS);
    exit_section_(b, m, null, r);
    return r;
  }

  public static boolean not_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "not_expression")) return false;
    if (!nextTokenIsSmart(b, T_NOT, T_BNOT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = not_expression_0(b, l + 1);
    p = r;
    r = p && expression(b, l, 14);
    exit_section_(b, l, m, UNARY_EXPRESSION, r, p, null);
    return r || p;
  }

  // '~' | '!'
  private static boolean not_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "not_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, T_BNOT);
    if (!r) r = consumeTokenSmart(b, T_NOT);
    exit_section_(b, m, null, r);
    return r;
  }

  // &cast_expression_start ('(' type_element ')' priority1_4)
  public static boolean cast_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "cast_expression")) return false;
    if (!nextTokenIsSmart(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = cast_expression_0(b, l + 1);
    r = r && cast_expression_1(b, l + 1);
    exit_section_(b, m, CAST_EXPRESSION, r);
    return r;
  }

  // &cast_expression_start
  private static boolean cast_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "cast_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = cast_expression_start(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '(' type_element ')' priority1_4
  private static boolean cast_expression_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "cast_expression_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, T_LPAREN);
    r = r && type_element(b, l + 1);
    r = r && consumeToken(b, T_RPAREN);
    r = r && expression(b, l + 1, 13);
    exit_section_(b, m, null, r);
    return r;
  }

  // fast_question index_expression_argument_list not_colon
  private static boolean safe_index_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "safe_index_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fast_question(b, l + 1);
    r = r && index_expression_argument_list(b, l + 1);
    r = r && not_colon(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '++' | '--'
  private static boolean postfix_unary_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "postfix_unary_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, T_INC);
    if (!r) r = consumeTokenSmart(b, T_DEC);
    exit_section_(b, m, null, r);
    return r;
  }

  // (mb_nl method_reference_dot) (mb_nl qualified_reference_expression_identifiers)
  private static boolean method_reference_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_reference_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = method_reference_expression_0_0(b, l + 1);
    r = r && method_reference_expression_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // mb_nl method_reference_dot
  private static boolean method_reference_expression_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_reference_expression_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && method_reference_dot(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // mb_nl qualified_reference_expression_identifiers
  private static boolean method_reference_expression_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_reference_expression_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && qualified_reference_expression_identifiers(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (mb_nl reference_dot) <<mb_nl_group (type_argument_list? qualified_reference_expression_identifiers)>>
  private static boolean qualified_reference_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_reference_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = qualified_reference_expression_0_0(b, l + 1);
    r = r && mb_nl_group(b, l + 1, qualified_reference_expression_0_1_0_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  // mb_nl reference_dot
  private static boolean qualified_reference_expression_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_reference_expression_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && reference_dot(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_argument_list? qualified_reference_expression_identifiers
  private static boolean qualified_reference_expression_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_reference_expression_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = qualified_reference_expression_0_1_0_0(b, l + 1);
    r = r && qualified_reference_expression_identifiers(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_argument_list?
  private static boolean qualified_reference_expression_0_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_reference_expression_0_1_0_0")) return false;
    type_argument_list(b, l + 1);
    return true;
  }

  // (mb_nl property_dot) <<mb_nl_group (type_argument_list? property_expression_identifiers)>>
  private static boolean property_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = property_expression_0_0(b, l + 1);
    r = r && mb_nl_group(b, l + 1, property_expression_0_1_0_parser_);
    exit_section_(b, m, null, r);
    return r;
  }

  // mb_nl property_dot
  private static boolean property_expression_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_expression_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mb_nl(b, l + 1);
    r = r && property_dot(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_argument_list? property_expression_identifiers
  private static boolean property_expression_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_expression_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = property_expression_0_1_0_0(b, l + 1);
    r = r && property_expression_identifiers(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_argument_list?
  private static boolean property_expression_0_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_expression_0_1_0_0")) return false;
    type_argument_list(b, l + 1);
    return true;
  }

  // <<parseBlockLazy closure 'CLOSURE'>>
  public static boolean lazy_closure(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lazy_closure")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, CLOSURE, "<lazy closure>");
    r = parseBlockLazy(b, l + 1, closure_parser_, CLOSURE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // empty_list | empty_map | non_empty_list_or_map
  public static boolean list_or_map(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_or_map")) return false;
    if (!nextTokenIsSmart(b, T_LBRACK)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = empty_list(b, l + 1);
    if (!r) r = empty_map(b, l + 1);
    if (!r) r = non_empty_list_or_map(b, l + 1);
    exit_section_(b, m, LIST_OR_MAP, r);
    return r;
  }

  // 'new' type_argument_list? anonymous_type_definition
  public static boolean new_anonymous_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "new_anonymous_expression")) return false;
    if (!nextTokenIsSmart(b, KW_NEW)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, KW_NEW);
    r = r && new_anonymous_expression_1(b, l + 1);
    r = r && anonymous_type_definition(b, l + 1);
    exit_section_(b, m, NEW_EXPRESSION, r);
    return r;
  }

  // type_argument_list?
  private static boolean new_anonymous_expression_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "new_anonymous_expression_1")) return false;
    type_argument_list(b, l + 1);
    return true;
  }

  // 'new' type_argument_list? new_expression_tail
  public static boolean new_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "new_expression")) return false;
    if (!nextTokenIsSmart(b, KW_NEW)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, NEW_EXPRESSION, null);
    r = consumeTokenSmart(b, KW_NEW);
    p = r; // pin = 1
    r = r && report_error_(b, new_expression_1(b, l + 1));
    r = p && new_expression_tail(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // type_argument_list?
  private static boolean new_expression_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "new_expression_1")) return false;
    type_argument_list(b, l + 1);
    return true;
  }

  // IDENTIFIER soft_type_argument_list?
  //                                    | 'this'
  //                                    | 'super'
  //                                    | code_reference_identifiers_soft &(reference_dot | '.&')
  //                                    | (STR_SQ | STR_DQ) &string_literal_as_reference
  public static boolean unqualified_reference_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unqualified_reference_expression")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, REFERENCE_EXPRESSION, "<unqualified reference expression>");
    r = unqualified_reference_expression_0(b, l + 1);
    if (!r) r = consumeTokenSmart(b, KW_THIS);
    if (!r) r = consumeTokenSmart(b, KW_SUPER);
    if (!r) r = unqualified_reference_expression_3(b, l + 1);
    if (!r) r = unqualified_reference_expression_4(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // IDENTIFIER soft_type_argument_list?
  private static boolean unqualified_reference_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unqualified_reference_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, IDENTIFIER);
    r = r && unqualified_reference_expression_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // soft_type_argument_list?
  private static boolean unqualified_reference_expression_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unqualified_reference_expression_0_1")) return false;
    soft_type_argument_list(b, l + 1);
    return true;
  }

  // code_reference_identifiers_soft &(reference_dot | '.&')
  private static boolean unqualified_reference_expression_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unqualified_reference_expression_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = code_reference_identifiers_soft(b, l + 1);
    r = r && unqualified_reference_expression_3_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &(reference_dot | '.&')
  private static boolean unqualified_reference_expression_3_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unqualified_reference_expression_3_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = unqualified_reference_expression_3_1_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // reference_dot | '.&'
  private static boolean unqualified_reference_expression_3_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unqualified_reference_expression_3_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = reference_dot(b, l + 1);
    if (!r) r = consumeTokenSmart(b, T_METHOD_CLOSURE);
    exit_section_(b, m, null, r);
    return r;
  }

  // (STR_SQ | STR_DQ) &string_literal_as_reference
  private static boolean unqualified_reference_expression_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unqualified_reference_expression_4")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = unqualified_reference_expression_4_0(b, l + 1);
    r = r && unqualified_reference_expression_4_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // STR_SQ | STR_DQ
  private static boolean unqualified_reference_expression_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unqualified_reference_expression_4_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, STR_SQ);
    if (!r) r = consumeTokenSmart(b, STR_DQ);
    return r;
  }

  // &string_literal_as_reference
  private static boolean unqualified_reference_expression_4_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unqualified_reference_expression_4_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = string_literal_as_reference(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // primitive_type
  public static boolean built_in_type_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "built_in_type_expression")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, BUILT_IN_TYPE_EXPRESSION, "<built in type expression>");
    r = parsePrimitiveType(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // simple_literal_tokens | regex_literal
  public static boolean literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LITERAL, "<literal>");
    r = simple_literal_tokens(b, l + 1);
    if (!r) r = regex_literal(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<compound_string GSTRING_BEGIN fast_string_content GSTRING_END>>
  public static boolean gstring(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gstring")) return false;
    if (!nextTokenIsSmart(b, GSTRING_BEGIN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = compound_string(b, l + 1, GSTRING_BEGIN_parser_, fast_string_content_parser_, GSTRING_END_parser_);
    exit_section_(b, m, GSTRING, r);
    return r;
  }

  // slashy_string | dollar_slashy_string
  public static boolean regex(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "regex")) return false;
    if (!nextTokenIsSmart(b, DOLLAR_SLASHY_BEGIN, SLASHY_BEGIN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, REGEX, "<regex>");
    r = slashy_string(b, l + 1);
    if (!r) r = dollar_slashy_string(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // p_parenthesized_expression_inner
  public static boolean parenthesized_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parenthesized_expression")) return false;
    if (!nextTokenIsSmart(b, T_LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = p_parenthesized_expression_inner(b, l + 1);
    exit_section_(b, m, PARENTHESIZED_EXPRESSION, r);
    return r;
  }

  final static Parser DOLLAR_SLASHY_BEGIN_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return consumeToken(b, DOLLAR_SLASHY_BEGIN);
    }
  };
  final static Parser DOLLAR_SLASHY_END_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return consumeToken(b, DOLLAR_SLASHY_END);
    }
  };
  final static Parser GSTRING_BEGIN_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return consumeToken(b, GSTRING_BEGIN);
    }
  };
  final static Parser GSTRING_END_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return consumeToken(b, GSTRING_END);
    }
  };
  final static Parser SLASHY_BEGIN_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return consumeToken(b, SLASHY_BEGIN);
    }
  };
  final static Parser SLASHY_END_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return consumeToken(b, SLASHY_END);
    }
  };
  final static Parser T_GT_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return consumeToken(b, T_GT);
    }
  };
  final static Parser T_LBRACE_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return consumeTokenFast(b, T_LBRACE);
    }
  };
  final static Parser T_RBRACK_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return consumeToken(b, T_RBRACK);
    }
  };
  final static Parser T_RPAREN_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return consumeToken(b, T_RPAREN);
    }
  };
  final static Parser annotation_level_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return annotation_level(b, l + 1);
    }
  };
  final static Parser annotation_member_value_pair_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return annotation_member_value_pair(b, l + 1);
    }
  };
  final static Parser annotation_method_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return annotation_method(b, l + 1);
    }
  };
  final static Parser annotation_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return annotation(b, l + 1);
    }
  };
  final static Parser annotation_tails_0_0_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return annotation_tails_0_0(b, l + 1);
    }
  };
  final static Parser annotation_tails_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return annotation_tails(b, l + 1);
    }
  };
  final static Parser annotation_value_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return annotation_value(b, l + 1);
    }
  };
  final static Parser application_arguments_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return application_arguments(b, l + 1);
    }
  };
  final static Parser application_call_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return application_call(b, l + 1);
    }
  };
  final static Parser application_expression_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return application_expression(b, l + 1);
    }
  };
  final static Parser application_index_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return application_index(b, l + 1);
    }
  };
  final static Parser application_ref_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return application_ref(b, l + 1);
    }
  };
  final static Parser argument_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return argument(b, l + 1);
    }
  };
  final static Parser block_declaration_start_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return block_declaration_start(b, l + 1);
    }
  };
  final static Parser block_declaration_tail_0_0_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return block_declaration_tail_0_0(b, l + 1);
    }
  };
  final static Parser block_declaration_tail_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return block_declaration_tail(b, l + 1);
    }
  };
  final static Parser block_level_end_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return block_level_end(b, l + 1);
    }
  };
  final static Parser block_level_start_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return block_level_start(b, l + 1);
    }
  };
  final static Parser call_tail_1_1_0_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return call_tail_1_1_0(b, l + 1);
    }
  };
  final static Parser capital_class_type_element_0_1_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return refWasCapitalized(b, l + 1);
    }
  };
  final static Parser case_level_end_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return case_level_end(b, l + 1);
    }
  };
  final static Parser case_level_start_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return case_level_start(b, l + 1);
    }
  };
  final static Parser case_section_recovery_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return case_section_recovery(b, l + 1);
    }
  };
  final static Parser catch_parameter_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return catch_parameter(b, l + 1);
    }
  };
  final static Parser catch_parameter_start_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return catch_parameter_start(b, l + 1);
    }
  };
  final static Parser class_declaration_start_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return class_declaration_start(b, l + 1);
    }
  };
  final static Parser class_declaration_tail_0_0_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return class_declaration_tail_0_0(b, l + 1);
    }
  };
  final static Parser class_declaration_tail_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return class_declaration_tail(b, l + 1);
    }
  };
  final static Parser class_level_end_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return class_level_end(b, l + 1);
    }
  };
  final static Parser class_level_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return class_level(b, l + 1);
    }
  };
  final static Parser class_level_start_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return class_level_start(b, l + 1);
    }
  };
  final static Parser class_type_element_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return class_type_element(b, l + 1);
    }
  };
  final static Parser closure_parameter_list_0_0_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return closure_parameter_list_0_0(b, l + 1);
    }
  };
  final static Parser closure_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return closure(b, l + 1);
    }
  };
  final static Parser code_reference_identifiers_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return code_reference_identifiers(b, l + 1);
    }
  };
  final static Parser code_reference_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return code_reference(b, l + 1);
    }
  };
  final static Parser constructor_block_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return constructor_block(b, l + 1);
    }
  };
  final static Parser definitely_type_element_0_1_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return definitely_type_element_0_1(b, l + 1);
    }
  };
  final static Parser expression_or_application_inner_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return expression_or_application_inner(b, l + 1);
    }
  };
  final static Parser expression_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return expression(b, l + 1, -1);
    }
  };
  final static Parser extends_list_item_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return extends_list_item(b, l + 1);
    }
  };
  final static Parser extends_recovery_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return extends_recovery(b, l + 1);
    }
  };
  final static Parser fast_comma_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return fast_comma(b, l + 1);
    }
  };
  final static Parser fast_dollar_slashy_content_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return fast_dollar_slashy_content(b, l + 1);
    }
  };
  final static Parser fast_slashy_content_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return fast_slashy_content(b, l + 1);
    }
  };
  final static Parser fast_string_content_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return fast_string_content(b, l + 1);
    }
  };
  final static Parser implements_list_item_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return implements_list_item(b, l + 1);
    }
  };
  final static Parser implements_recovery_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return implements_recovery(b, l + 1);
    }
  };
  final static Parser mb_nl_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return mb_nl(b, l + 1);
    }
  };
  final static Parser methods_tail_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return methods_tail(b, l + 1);
    }
  };
  final static Parser naked_method_declaration_start_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return naked_method_declaration_start(b, l + 1);
    }
  };
  final static Parser non_empty_modifier_list_0_0_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return non_empty_modifier_list_0_0(b, l + 1);
    }
  };
  final static Parser open_block_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return open_block(b, l + 1);
    }
  };
  final static Parser parameter_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return parameter(b, l + 1);
    }
  };
  final static Parser parameter_start_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return parameter_start(b, l + 1);
    }
  };
  final static Parser paren_list_item_recovery_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return paren_list_item_recovery(b, l + 1);
    }
  };
  final static Parser parse_parameter_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return parse_parameter(b, l + 1);
    }
  };
  final static Parser property_expression_0_1_0_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return property_expression_0_1_0(b, l + 1);
    }
  };
  final static Parser qualified_reference_expression_0_1_0_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return qualified_reference_expression_0_1_0(b, l + 1);
    }
  };
  final static Parser statement_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return statement(b, l + 1);
    }
  };
  final static Parser throws_list_item_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return throws_list_item(b, l + 1);
    }
  };
  final static Parser top_level_end_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return top_level_end(b, l + 1);
    }
  };
  final static Parser top_level_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return top_level(b, l + 1);
    }
  };
  final static Parser top_level_start_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return top_level_start(b, l + 1);
    }
  };
  final static Parser tuple_var_declaration_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return tuple_var_declaration(b, l + 1);
    }
  };
  final static Parser type_argument_list_item_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return type_argument_list_item(b, l + 1);
    }
  };
  final static Parser type_element_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return type_element(b, l + 1);
    }
  };
  final static Parser variable_declaration_tail_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return variable_declaration_tail(b, l + 1);
    }
  };
}

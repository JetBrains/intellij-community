package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.psi.tree.IElementType;

/**
 * Interface, that contains all tokens, retruned by GroovyLexer
 *
 * @author Ilya.Sergey
 */
public interface GroovyTokenTypes {

  /**
   * Wrong token. Use for debug needs
   */
  IElementType WRONG = new GroovyElementType("wrong token");

  /* **************************************************************************************************
 *  Comments
 * ****************************************************************************************************/

  IElementType SH_COMMENT = new GroovyElementType("shell comment");
  IElementType SL_COMMENT = new GroovyElementType("line comment");
  IElementType ML_COMMENT = new GroovyElementType("block comment");

  /* **************************************************************************************************
 *  Whitespaces & NewLinew
 * ****************************************************************************************************/

  IElementType WS = new GroovyElementType("White space");

  /* **************************************************************************************************
 *  Keywords
 * ****************************************************************************************************/
  IElementType LITERAL_case = new GroovyElementType("case");

  IElementType LITERAL_instanceof = new GroovyElementType("");

  IElementType LITERAL_try = new GroovyElementType("");

  IElementType LITERAL_finally = new GroovyElementType("");

  IElementType LITERAL_catch = new GroovyElementType("");

  IElementType LITERAL_default = new GroovyElementType("");

  IElementType LITERAL_throws = new GroovyElementType("");

  IElementType LITERAL_implements = new GroovyElementType("");

  IElementType LITERAL_this = new GroovyElementType("");

  IElementType LITERAL_extends = new GroovyElementType("");

  IElementType LITERAL_super = new GroovyElementType("");

  IElementType LITERAL_if = new GroovyElementType("");

  IElementType LITERAL_else = new GroovyElementType("");

  IElementType LITERAL_while = new GroovyElementType("");

  IElementType LITERAL_with = new GroovyElementType("");

  IElementType LITERAL_switch = new GroovyElementType("");

  IElementType LITERAL_class = new GroovyElementType("");

  IElementType LITERAL_interface = new GroovyElementType("");

  IElementType LITERAL_enum = new GroovyElementType("");

  IElementType LITERAL_package = new GroovyElementType("");

  IElementType LITERAL_import = new GroovyElementType("");

  IElementType LITERAL_static = new GroovyElementType("");

  IElementType LITERAL_def = new GroovyElementType("");

  IElementType LITERAL_for = new GroovyElementType("");

  IElementType LITERAL_in = new GroovyElementType("");

  IElementType LITERAL_return = new GroovyElementType("");

  IElementType LITERAL_break = new GroovyElementType("");

  IElementType LITERAL_continue = new GroovyElementType("");

  IElementType LITERAL_throw = new GroovyElementType("");

  IElementType LITERAL_assert = new GroovyElementType("");

  IElementType LITERAL_new = new GroovyElementType("");

  IElementType LITERAL_true = new GroovyElementType("");

  IElementType LITERAL_false = new GroovyElementType("");

  IElementType LITERAL_null = new GroovyElementType("");

  IElementType LITERAL_as = new GroovyElementType("");

  IElementType LITERAL_void = new GroovyElementType("");

  IElementType LITERAL_boolean = new GroovyElementType("");

  IElementType LITERAL_byte = new GroovyElementType("");

  IElementType LITERAL_char = new GroovyElementType("");

  IElementType LITERAL_short = new GroovyElementType("");

  IElementType LITERAL_IElementType = new GroovyElementType("");

  IElementType LITERAL_float = new GroovyElementType("");

  IElementType LITERAL_long = new GroovyElementType("");

  IElementType LITERAL_double = new GroovyElementType("");

  IElementType LITERAL_any = new GroovyElementType("");

  IElementType LITERAL_private = new GroovyElementType("");

  IElementType LITERAL_public = new GroovyElementType("");

  IElementType LITERAL_protected = new GroovyElementType("");

  IElementType LITERAL_transient = new GroovyElementType("");

  IElementType LITERAL_native = new GroovyElementType("");

  IElementType LITERAL_threadsafe = new GroovyElementType("");

  IElementType LITERAL_synchronized = new GroovyElementType("");

  IElementType LITERAL_volatile = new GroovyElementType("");

  /*
  IElementType REGEXP_LITERAL = new GroovyElementType("");

 7

   IElementType EOF = 1;

8

   IElementType NULL_TREE_LOOKAHEAD = 3;

9

   IElementType BLOCK = 4;

new GroovyElementType("")

   IElementType MODIFIERS = 5;

new GroovyElementType("")

   IElementType OBJBLOCK = 6;

new GroovyElementType("")

   IElementType SLIST = 7;

new GroovyElementType("")

   IElementType METHOD_DEF = 8;

new GroovyElementType("")

   IElementType VARIABLE_DEF = 9;

new GroovyElementType("")

   IElementType INSTANCE_INIT = new GroovyElementType("");

new GroovyElementType("")

   IElementType STATIC_INIT = new GroovyElementType("");

new GroovyElementType("")

   IElementType TYPE = new GroovyElementType("");

new GroovyElementType("")

   IElementType CLASS_DEF = new GroovyElementType("");

new GroovyElementType("")

   IElementType IElementTypeERFACE_DEF = new GroovyElementType("");

new GroovyElementType("")

   IElementType PACKAGE_DEF = new GroovyElementType("");

new GroovyElementType("")

   IElementType ARRAY_DECLARATOR = new GroovyElementType("");

new GroovyElementType("")

   IElementType EXTENDS_CLAUSE = new GroovyElementType("");

new GroovyElementType("")

   IElementType IMPLEMENTS_CLAUSE = new GroovyElementType("");

new GroovyElementType("")

   IElementType PARAMETERS = new GroovyElementType("");

new GroovyElementType("")

   IElementType PARAMETER_DEF = new GroovyElementType("");

new GroovyElementType("")

   IElementType LABELED_STAT = new GroovyElementType("");

new GroovyElementType("")

   IElementType TYPECAST = new GroovyElementType("");

new GroovyElementType("")

   IElementType INDEX_OP = new GroovyElementType("");

new GroovyElementType("")

   IElementType POST_INC = new GroovyElementType("");

new GroovyElementType("")

   IElementType POST_DEC = new GroovyElementType("");

new GroovyElementType("")

   IElementType METHOD_CALL = new GroovyElementType("");

new GroovyElementType("")

   IElementType EXPR = new GroovyElementType("");

new GroovyElementType("")

   IElementType IMPORT = new GroovyElementType("");

new GroovyElementType("")

   IElementType UNARY_MINUS = new GroovyElementType("");

new GroovyElementType("")

   IElementType UNARY_PLUS = new GroovyElementType("");

new GroovyElementType("")

   IElementType CASE_GROUP = new GroovyElementType("");

new GroovyElementType("")

   IElementType ELIST = new GroovyElementType("");

new GroovyElementType("")

   IElementType FOR_INIT = new GroovyElementType("");

new GroovyElementType("")

   IElementType FOR_CONDITION = new GroovyElementType("");

new GroovyElementType("")

   IElementType FOR_ITERATOR = new GroovyElementType("");

new GroovyElementType("")

   IElementType EMPTY_STAT = new GroovyElementType("");

new GroovyElementType("")

   IElementType FINAL = new GroovyElementType("");

new GroovyElementType("")

   IElementType ABSTRACT = new GroovyElementType("");

new GroovyElementType("")

   IElementType UNUSED_GOTO = new GroovyElementType("");

new GroovyElementType("")

   IElementType UNUSED_CONST = new GroovyElementType("");

new GroovyElementType("")

   IElementType UNUSED_DO = new GroovyElementType("");

new GroovyElementType("")

   IElementType STRICTFP = new GroovyElementType("");

new GroovyElementType("")

   IElementType SUPER_CTOR_CALL = new GroovyElementType("");

new GroovyElementType("")

   IElementType CTOR_CALL = new GroovyElementType("");

new GroovyElementType("")

   IElementType CTOR_IDENT = new GroovyElementType("");

new GroovyElementType("")

   IElementType VARIABLE_PARAMETER_DEF = new GroovyElementType("");

new GroovyElementType("")

   IElementType STRING_CONSTRUCTOR = new GroovyElementType("");

new GroovyElementType("")

   IElementType STRING_CTOR_MIDDLE = new GroovyElementType("");

new GroovyElementType("")

   IElementType CLOSED_BLOCK = new GroovyElementType("");

new GroovyElementType("")

   IElementType IMPLICIT_PARAMETERS = new GroovyElementType("");

new GroovyElementType("")

   IElementType SELECT_SLOT = new GroovyElementType("");

new GroovyElementType("")

   IElementType DYNAMIC_MEMBER = new GroovyElementType("");

new GroovyElementType("")

   IElementType LABELED_ARG = new GroovyElementType("");

new GroovyElementType("")

   IElementType SPREAD_ARG = new GroovyElementType("");

new GroovyElementType("")

   IElementType SPREAD_MAP_ARG = new GroovyElementType("");

new GroovyElementType("")

   IElementType SCOPE_ESCAPE = new GroovyElementType("");

new GroovyElementType("")

   IElementType LIST_CONSTRUCTOR = new GroovyElementType("");

new GroovyElementType("")

   IElementType MAP_CONSTRUCTOR = new GroovyElementType("");

new GroovyElementType("")

   IElementType FOR_IN_ITERABLE = new GroovyElementType("");

new GroovyElementType("")

   IElementType STATIC_IMPORT = new GroovyElementType("");

new GroovyElementType("")

   IElementType ENUM_DEF = new GroovyElementType("");

new GroovyElementType("")

   IElementType ENUM_CONSTANT_DEF = new GroovyElementType("");

new GroovyElementType("")

   IElementType FOR_EACH_CLAUSE = new GroovyElementType("");

new GroovyElementType("")

   IElementType ANNOTATION_DEF = new GroovyElementType("");

new GroovyElementType("")

   IElementType ANNOTATIONS = new GroovyElementType("");

new GroovyElementType("")

   IElementType ANNOTATION = new GroovyElementType("");

new GroovyElementType("")

   IElementType ANNOTATION_MEMBER_VALUE_PAIR = new GroovyElementType("");

new GroovyElementType("")

   IElementType ANNOTATION_FIELD_DEF = new GroovyElementType("");

new GroovyElementType("")

   IElementType ANNOTATION_ARRAY_INIT = new GroovyElementType("");

new GroovyElementType("")

   IElementType TYPE_ARGUMENTS = new GroovyElementType("");

new GroovyElementType("")

   IElementType TYPE_ARGUMENT = new GroovyElementType("");

new GroovyElementType("")

   IElementType TYPE_PARAMETERS = new GroovyElementType("");

new GroovyElementType("")
         IElementType STRING_LITERAL = new GroovyElementType("");
   IElementType TYPE_PARAMETER = new GroovyElementType("");

new GroovyElementType("")

   IElementType WILDCARD_TYPE = new GroovyElementType("");

new GroovyElementType("")

   IElementType TYPE_UPPER_BOUNDS = new GroovyElementType("");

new GroovyElementType("")

   IElementType TYPE_LOWER_BOUNDS = new GroovyElementType("");

new GroovyElementType("")

   IElementType SH_COMMENT = new GroovyElementType("");

new GroovyElementType("")



new GroovyElementType("")

   IElementType AT = new GroovyElementType("");

new GroovyElementType("")

   IElementType IDENT = new GroovyElementType("");

new GroovyElementType("")

   IElementType LBRACK = new GroovyElementType("");

new GroovyElementType("")

   IElementType RBRACK = new GroovyElementType("");

new GroovyElementType("")

   IElementType DOT = new GroovyElementType("");

new GroovyElementType("")

   IElementType LPAREN = new GroovyElementType("");

new GroovyElementType("")


new GroovyElementType("")

   IElementType QUESTION = new GroovyElementType("");

new GroovyElementType("")



new GroovyElementType("")

   IElementType LT = new GroovyElementType("");

new GroovyElementType("")

   IElementType COMMA = new GroovyElementType("");

new GroovyElementType("")

   IElementType GT = new GroovyElementType("");

new GroovyElementType("")

   IElementType SR = new GroovyElementType("");

new GroovyElementType("")

   IElementType BSR = new GroovyElementType("");

new GroovyElementType("")



new GroovyElementType("")

   IElementType STAR = new GroovyElementType("");



new GroovyElementType("")

   IElementType RPAREN = new GroovyElementType("");

new GroovyElementType("")

   IElementType ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType BAND = new GroovyElementType("");

new GroovyElementType("")

   IElementType LCURLY = new GroovyElementType("");

new GroovyElementType("")

   IElementType RCURLY = new GroovyElementType("");

new GroovyElementType("")

   IElementType SEMI = new GroovyElementType("");

new GroovyElementType("")

   IElementType NLS = new GroovyElementType("");

new GroovyElementType("")



new GroovyElementType("")

   IElementType TRIPLE_DOT = new GroovyElementType("");

new GroovyElementType("")

   IElementType CLOSURE_OP = new GroovyElementType("");

new GroovyElementType("")

   IElementType LOR = new GroovyElementType("");

new GroovyElementType("")

   IElementType BOR = new GroovyElementType("");

new GroovyElementType("")

   IElementType COLON = new GroovyElementType("");



new GroovyElementType("")

   IElementType PLUS = new GroovyElementType("");

new GroovyElementType("")

   IElementType MINUS = new GroovyElementType("");

new GroovyElementType("")



new GroovyElementType("")

   IElementType SPREAD_DOT = new GroovyElementType("");

new GroovyElementType("")

   IElementType OPTIONAL_DOT = new GroovyElementType("");

new GroovyElementType("")

   IElementType MEMBER_POIElementTypeER = new GroovyElementType("");

new GroovyElementType("")

   IElementType PLUS_ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType MINUS_ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType STAR_ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType DIV_ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType MOD_ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType SR_ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType BSR_ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType SL_ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType BAND_ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType BXOR_ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType BOR_ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType STAR_STAR_ASSIGN = new GroovyElementType("");

new GroovyElementType("")

   IElementType LAND = new GroovyElementType("");

new GroovyElementType("")

   IElementType BXOR = new GroovyElementType("");

new GroovyElementType("")

   IElementType REGEX_FIND = new GroovyElementType("");

new GroovyElementType("")

   IElementType REGEX_MATCH = new GroovyElementType("");

new GroovyElementType("")

   IElementType NOT_EQUAL = new GroovyElementType("");

new GroovyElementType("")

   IElementType EQUAL = new GroovyElementType("");


   IElementType COMPARE_TO = new GroovyElementType("");


   IElementType LE = new GroovyElementType("");


   IElementType GE = new GroovyElementType("");


   IElementType SL = new GroovyElementType("");


   IElementType RANGE_INCLUSIVE = new GroovyElementType("");


   IElementType RANGE_EXCLUSIVE = new GroovyElementType("");


   IElementType INC = new GroovyElementType("");


   IElementType DIV = new GroovyElementType("");


   IElementType MOD = new GroovyElementType("");


   IElementType DEC = new GroovyElementType("");


   IElementType STAR_STAR = new GroovyElementType("");


   IElementType BNOT = new GroovyElementType("");


   IElementType LNOT = new GroovyElementType("");


   IElementType DOLLAR = new GroovyElementType("");


   IElementType STRING_CTOR_START = new GroovyElementType("");


   IElementType STRING_CTOR_END = new GroovyElementType("");


   IElementType NUM_IElementType = new GroovyElementType("");


   IElementType NUM_FLOAT = new GroovyElementType("");


   IElementType NUM_LONG = new GroovyElementType("");


   IElementType NUM_DOUBLE = new GroovyElementType("");


   IElementType NUM_BIG_IElementType = new GroovyElementType("");


   IElementType NUM_BIG_DECIMAL = new GroovyElementType("");


   IElementType WS = new GroovyElementType("");


   IElementType ONE_NL = new GroovyElementType("");


   IElementType SL_COMMENT = new GroovyElementType("");


   IElementType ML_COMMENT = new GroovyElementType("");


   IElementType STRING_CH = new GroovyElementType("");


   IElementType REGEXP_CTOR_END = new GroovyElementType("");


   IElementType REGEXP_SYMBOL = new GroovyElementType("");


   IElementType ESC = new GroovyElementType("");


   IElementType STRING_NL = new GroovyElementType("");


   IElementType HEX_DIGIT = new GroovyElementType("");


   IElementType VOCAB = new GroovyElementType("");


   IElementType LETTER = new GroovyElementType("");


   IElementType DIGIT = new GroovyElementType("");


   IElementType EXPONENT = new GroovyElementType("");


   IElementType FLOAT_SUFFIX = new GroovyElementType("");


   IElementType BIG_SUFFIX = new GroovyElementType("");
  */
}

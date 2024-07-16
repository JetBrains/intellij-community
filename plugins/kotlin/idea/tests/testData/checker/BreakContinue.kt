class C {

    fun f (<warning descr="[UNUSED_PARAMETER] Parameter 'a' is never used">a</warning> : Boolean, <warning descr="[UNUSED_PARAMETER] Parameter 'b' is never used">b</warning> : Boolean) {
        b@ (<error descr="[EXPRESSION_EXPECTED] While is not an expression, and only expressions are allowed here">while (true)
          <warning descr="[REDUNDANT_LABEL_WARNING] Label is redundant, because it can not be referenced in either 'break', 'continue', or 'return' expression">a@</warning> {
            <error descr="[NOT_A_LOOP_LABEL] The label '@f' does not denote a loop">break@f</error>
            break
            <warning descr="[UNREACHABLE_CODE] Unreachable code">break@b</warning>
            <error descr="[NOT_A_LOOP_LABEL] The label '@a' does not denote a loop">break@a</error>
          }</error>)

        <error descr="[BREAK_OR_CONTINUE_OUTSIDE_A_LOOP] 'break' and 'continue' are only allowed inside a loop">continue</error>

        b@ (<error descr="[EXPRESSION_EXPECTED] While is not an expression, and only expressions are allowed here">while (true)
          <warning descr="[REDUNDANT_LABEL_WARNING] Label is redundant, because it can not be referenced in either 'break', 'continue', or 'return' expression">a@</warning> {
            <error descr="[NOT_A_LOOP_LABEL] The label '@f' does not denote a loop">continue@f</error>
            continue
            <warning descr="[UNREACHABLE_CODE] Unreachable code">continue@b</warning>
            <error descr="[NOT_A_LOOP_LABEL] The label '@a' does not denote a loop">continue@a</error>
          }</error>)

        <error descr="[BREAK_OR_CONTINUE_OUTSIDE_A_LOOP] 'break' and 'continue' are only allowed inside a loop">break</error>

        <error descr="[NOT_A_LOOP_LABEL] The label '@f' does not denote a loop">continue@f</error>
        <error descr="[NOT_A_LOOP_LABEL] The label '@f' does not denote a loop">break@f</error>
    }

}
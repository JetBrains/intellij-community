class C {

    fun f (<warning descr="[UNUSED_PARAMETER]">a</warning> : Boolean, <warning descr="[UNUSED_PARAMETER]">b</warning> : Boolean) {
        b@ (<error descr="[EXPRESSION_EXPECTED]">while (true)
          <warning descr="[REDUNDANT_LABEL_WARNING]">a@</warning> {
            <error descr="[NOT_A_LOOP_LABEL]">break@f</error>
            break
            <warning descr="[UNREACHABLE_CODE]">break@b</warning>
            <error descr="[NOT_A_LOOP_LABEL]">break@a</error>
          }</error>)

        <error descr="[BREAK_OR_CONTINUE_OUTSIDE_A_LOOP]">continue</error>

        b@ (<error descr="[EXPRESSION_EXPECTED]">while (true)
          <warning descr="[REDUNDANT_LABEL_WARNING]">a@</warning> {
            <error descr="[NOT_A_LOOP_LABEL]">continue@f</error>
            continue
            <warning descr="[UNREACHABLE_CODE]">continue@b</warning>
            <error descr="[NOT_A_LOOP_LABEL]">continue@a</error>
          }</error>)

        <error descr="[BREAK_OR_CONTINUE_OUTSIDE_A_LOOP]">break</error>

        <error descr="[NOT_A_LOOP_LABEL]">continue@f</error>
        <error descr="[NOT_A_LOOP_LABEL]">break@f</error>
    }

}
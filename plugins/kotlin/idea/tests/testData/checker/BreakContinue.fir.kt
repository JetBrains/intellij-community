class C {

    fun f (a : Boolean, b : Boolean) {
        b@ (while (true)
          a@ {
            <error descr="[NOT_A_LOOP_LABEL]">break@f</error>
            break
            break@b
            <error descr="[NOT_A_LOOP_LABEL]">break@a</error>
          })

        <error descr="[BREAK_OR_CONTINUE_OUTSIDE_A_LOOP]">continue</error>

        b@ (while (true)
          a@ {
            <error descr="[NOT_A_LOOP_LABEL]">continue@f</error>
            continue
            continue@b
            <error descr="[NOT_A_LOOP_LABEL]">continue@a</error>
          })

        <error descr="[BREAK_OR_CONTINUE_OUTSIDE_A_LOOP]">break</error>

        <error descr="[BREAK_OR_CONTINUE_OUTSIDE_A_LOOP]">continue@f</error>
        <error descr="[BREAK_OR_CONTINUE_OUTSIDE_A_LOOP]">break@f</error>
    }

}

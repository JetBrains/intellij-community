fun test(i: Int): Int {
    return if (i == 1)
        // if
        1
        // if2
    else if (i == 2)
        // elseif
        2
        // elseif2
    <caret>else
        // else
        3
}
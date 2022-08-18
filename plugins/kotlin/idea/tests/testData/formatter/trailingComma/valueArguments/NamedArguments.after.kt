fun check() {
    check(
        /* a = */ 1,
        /* a = */ 1,
        /* b = */ "42"
    )

    check(
        /* c = */ 2,
        /* c2 = */ 3,/* d = */ "24",
    )

    check(
        2, 3, "24", /* custom block comment */
    )

    check(
        2, 3, "24", // custom EOL comment
    )

    check(
        2, 3, "24" /* custom block comment */
    )

    check(
        2, 3, "24" // custom EOL comment
    )

    check(
        2, 3, /* custom block comment */
        "24", /* custom block comment */
    )

    check(
        2, 3, // custom EOL comment
        "24", // custom EOL comment
    )

    check(
        2, 3, /* custom block comment */
        "24" /* custom block comment */
    )

    check(
        2, 3, // custom EOL comment
        "24" // custom EOL comment
    )
}

// SET_FALSE: ALLOW_TRAILING_COMMA

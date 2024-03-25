fun bar(arg: Int): String {
    <caret>if (arg == 1) return "One"
    else if (2 == arg) return "Two"
    if (arg == 0) return "Zero"
    if (-1 == arg) return "Minus One"
    else if (arg == -2) return "Minus Two"
    return "Something Complex"
}
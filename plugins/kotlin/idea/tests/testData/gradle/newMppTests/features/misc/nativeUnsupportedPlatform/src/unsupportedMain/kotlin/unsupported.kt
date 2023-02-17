fun main() =
    <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: platform'")!>platform<!>
        .<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>posix<!>
        .<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>fopen<!>("myfile.txt", "r")

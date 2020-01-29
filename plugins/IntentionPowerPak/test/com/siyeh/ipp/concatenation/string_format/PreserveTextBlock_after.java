class C {
    //keep me
    String s = String.format("""
              the text\s
             block
              line2
            %d%d <caret>to be""", 1, 2);
}
def x = 3;
def s = x + x+(x + "a${x}") +<caret> " asd " + x.toString();
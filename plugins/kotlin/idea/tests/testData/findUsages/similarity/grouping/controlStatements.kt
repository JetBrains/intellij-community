fun get<caret>Bar(): String {
    return ""
}

fun getInt(): Int {
    return 1
}

fun foo() {
    if (getBar().isEmpty()) { //one statement print simple string
        print("bar");
    }

    if (getBar().isEmpty()) { //one statement print simple string
        print("baz");
    }

    if (getBar().isEmpty()) { //print expression with print some calculation
        print(getInt()+1)
    }

    if (!getBar().isEmpty() && getInt() > 1) { //'complex' statement
        print("start process")
        while (true) {
            print("processing")
        }
    }

    if (!getBar().isEmpty() && getInt() > 1) { // 'complex' statement
        print("start process")
        print(1);
        print(2);
        print(3);
    }
}
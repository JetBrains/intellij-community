java.lang.String s = """

<?php
\t\r\b\f\\
""\"a""\"
class test {
    public function xyz() {
        // note trailing space on the next line
        echo 'xyz';\s
    }
}

    $object = new test;
    $object->xyz();
""" + "\n$abc = '123';";
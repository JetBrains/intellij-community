java.lang.String s = '''
<?php
\t\r\b\f\\
"""a"""
class test {
    public function xyz() {
        // note trailing space on the next line
        echo 'xyz'; 
    }
}

    $object = new test;
    $object-><caret>xyz();
''' + "\n\$abc = '123';"

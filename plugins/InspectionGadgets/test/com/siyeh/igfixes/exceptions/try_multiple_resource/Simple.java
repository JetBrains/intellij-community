import java.io.*;
class Simple {
    void foo(File file1, File file2) throws IOException {
        <caret>try (FileInputStream in = new FileInputStream(file1); FileOutputStream out = new FileOutputStream(file2)) {
            System.out.println(in + ", " + out);
        }
    }
}
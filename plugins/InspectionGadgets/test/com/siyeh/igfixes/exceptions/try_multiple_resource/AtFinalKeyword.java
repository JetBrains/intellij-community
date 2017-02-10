import java.io.*;
class AtFinalKeyword {
    void foo(File file1, File file2) throws IOException {
        try (f<caret>inal FileInputStream in = new FileInputStream(file1); FileOutputStream out = new FileOutputStream(file2)) {
            System.out.println(in + ", " + out);
        }
    }
}
import java.io.*;
class AtFinalKeyword {
    void foo(File file1, File file2) throws IOException {
        try (final FileInputStream in = new FileInputStream(file1)) {
            try (FileOutputStream out = new FileOutputStream(file2)) {
                System.out.println(in + ", " + out);
            }
        }
    }
}
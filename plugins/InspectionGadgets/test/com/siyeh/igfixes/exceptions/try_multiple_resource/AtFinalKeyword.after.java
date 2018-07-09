import java.io.*;
class AtFinalKeyword {
    void foo(File file1, File file2) throws IOException {
        //after first resource var
        //after end
        try (final FileInputStream in = /*comment in first*/new FileInputStream(file1)) {
            try (FileOutputStream out /*comment in out*/ = new FileOutputStream(file2)) {//comment
                System.out.println(in + ", " /*inside statement*/ + out); //commnt at sout
            }
        }
    }
}
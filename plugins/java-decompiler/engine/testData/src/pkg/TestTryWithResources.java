package pkg;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestTryWithResources {
  public static void test1() {
     try(FileSystem fileSystem = FileSystems.getFileSystem(TestTryWithResources.class.getResource("NOT").toURI())) {
       fileSystem.getPath("PATH", "TO", "FILE");
     }
     catch (URISyntaxException | IOException e) {}
  }

  public static void test2() {
    try(FileSystem fileSystem = FileSystems.getFileSystem(TestTryWithResources.class.getResource("NOT").toURI());
        InputStream stream = Files.newInputStream(fileSystem.getPath("PATH", "TO", "FILE"))) {
      stream.read();
    }
    catch (URISyntaxException | IOException e) {}
  }

  public static void test3() {
    try(FileSystem fileSystem = FileSystems.getFileSystem(TestTryWithResources.class.getResource("NOT").toURI())) {
      try (InputStream stream = Files.newInputStream(fileSystem.getPath("PATH", "TO", "FILE"))) {
        stream.read();
      }
      catch (IOException e) {}
      catch (Exception e) {}
    }
    catch (URISyntaxException | IOException e) {}
  }
}
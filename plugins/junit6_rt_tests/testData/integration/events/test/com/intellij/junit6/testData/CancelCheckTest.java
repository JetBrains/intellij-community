package com.intellij.junit6.testData;

import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CancelCheckTest {
  private static Path LOCK;

  @BeforeAll public static void start() throws IOException {
    LOCK = Files.createTempFile("test", ".lock");

    System.out.println("\nlock:" + LOCK.toAbsolutePath());
    System.out.flush();
  }

  @AfterAll public static void finish() {
    System.out.print("finish");
  }

  @Order(1) @Test public void test1() {run();}
  @Order(2) @Test public void test2() {run();}
  @Order(3) @Test public void test3() {run();}

  private void run() {
    int maxIterations = 100;
    // wait stop signal (SIGINT)
    try {
      while (Files.exists(LOCK) && maxIterations-- > 0) {
        Thread.sleep(100);
      }
      if (maxIterations <= 0) {
        throw new RuntimeException("File was not deleted: " + LOCK);
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
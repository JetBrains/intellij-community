package foo

import groovy.io.EncodingAwareBufferedWriter
import groovy.io.PlatformLineWriter

public class TestClass {

  def TestClass() {
    def pf = new PlatformLineWriter()
    def eab = new EncodingAwareBufferedWriter()
  }

}
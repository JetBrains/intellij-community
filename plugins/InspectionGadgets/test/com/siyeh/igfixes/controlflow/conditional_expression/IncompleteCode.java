class X {

    String x(String mockedUrl, String url) {
      return mockedUrl == null <caret>|| mockedUrl.equals(url) ? submit(() -> content) : ;
    }

    String submit(Runnable r) {
      return "";
    }
}
class X {

    String x(String mockedUrl, String url) {
        if (mockedUrl == null || mockedUrl.equals(url)) return submit(() -> content);<caret>
    }

    String submit(Runnable r) {
      return "";
    }
}
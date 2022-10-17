public class MyJavaFile {
    private final String path;

    public MyJavaFile(String userPath) {
        path = userPath;
    }

    public String getPath() {
        return path;
    }

    public String getAbsolutePath() {
        return path;
    }

    public String getAbsoluteFile() {
        return path;
    }

    public boolean isAbsolute() {
        return true;
    }
}

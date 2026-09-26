class JavaAccount {
    private final String email;
    private final String password;
    private final int flags;

    JavaAccount(String email, String password) {
        this(email, password, 0);
    }

    JavaAccount(String email, String password, int flags) {
        this.email = email;
        this.password = password;
        this.flags = flags;
    }
}
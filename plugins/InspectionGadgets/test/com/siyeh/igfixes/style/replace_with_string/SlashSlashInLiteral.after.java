class SlashSlashInLiteral {
    // comment
    final String s1 = "jdbc:mysql://" + "a" +
            '/' + "b" +
            '?' + "user" + '=' + "c" +
            "&amp;" + "password" + '=' + "d";
}
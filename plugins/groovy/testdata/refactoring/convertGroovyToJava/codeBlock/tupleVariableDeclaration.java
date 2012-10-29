java.lang.String s = "1";
int x = 2;

java.lang.String s = "1";
java.lang.Integer x = 2;

final java.util.Iterator<java.io.Serializable> iterator = java.util.Arrays.asList("2", 1).iterator();
java.lang.String s = ((java.lang.String)(iterator.hasNext() ? iterator.next() : null));
java.lang.Object x = iterator.hasNext() ? iterator.next() : null;


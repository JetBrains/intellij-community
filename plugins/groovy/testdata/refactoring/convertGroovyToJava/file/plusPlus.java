public class PlusPlus {
public static void main(java.lang.String[] args) {
java.lang.Integer i = 4;
i++;
assert i == 5;
java.lang.Integer a = ++i + i++;
assert i == 7;
assert a == 12;
}

}

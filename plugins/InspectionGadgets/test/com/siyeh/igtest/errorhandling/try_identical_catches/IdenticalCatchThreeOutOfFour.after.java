class Main {
    static class Ex1 extends Exception {}
    static class Ex2 extends Exception {}
    static class Ex3 extends Ex1 {}

    public void test() {
        try {
            if(Math.random() > 0.5) {
                throw new Ex1();
            }
            if(Math.random() > 0.5) {
                throw new Ex2();
            }
            if(Math.random() > 0.5) {
                throw new Ex3();
            }
        } catch(Ex3 ignored) {
        } catch(RuntimeException | Ex2 | Ex1 ex) {
            ex.printStackTrace();
        } catch(Error e) {
            return;
        }
    }
}
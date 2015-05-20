// "Create field for parameter 'p1'" "true"

class Test{
    int p1;
    int myP2;
 
    void f(int p1, int p2){
        this.p1 = p1;
        int myP2 = p1;
        p1 = 0;
    }
}


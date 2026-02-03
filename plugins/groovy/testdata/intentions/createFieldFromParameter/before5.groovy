// "Create field for parameter 'p1'" "true"

class Test{
    int p1;
    int myP2;
 
    Test(int p<caret>1, int p2){
        myP2 = p2;
    }
}
print(new java.lang.Runnable() {
public void run(java.lang.Object it) {print("foo}");}
public void run() {
this.run(null);
}
});
print(new java.lang.Runnable() {
public void run(java.lang.Object a) {print("foo}");}
public void run() {
this.run(null);
}
});

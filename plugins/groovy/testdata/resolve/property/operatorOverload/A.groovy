class Tick {
    public int seconds;

    Tick plus(int i) {
        return new Tick(seconds: seconds + i)
    }

    Object plus(String i) {
        return new Tick(seconds: seconds + i)
    }
}

def t = new Tick()
print((t + 1).<ref>seconds)
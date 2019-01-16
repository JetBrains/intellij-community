package ghostawt;

public abstract class GObjectPeer {
    protected Object target;
    
    public Object getTarget() {
        return target;
    }

    protected GObjectPeer(Object target) {
        this.target = target;
    }
}
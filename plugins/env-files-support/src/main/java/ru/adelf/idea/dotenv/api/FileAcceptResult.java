package ru.adelf.idea.dotenv.api;

public class FileAcceptResult {
    public final static FileAcceptResult ACCEPTED = new FileAcceptResult(true, true);
    public final static FileAcceptResult NOT_ACCEPTED = new FileAcceptResult(false, true);
    public final static FileAcceptResult ACCEPTED_SECONDARY = new FileAcceptResult(true, false);

    private final boolean accepted;
    private final boolean primary;

    private FileAcceptResult(boolean accepted, boolean primary) {
        this.accepted = accepted;
        this.primary = primary;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public boolean isPrimary() {
        return primary;
    }
}

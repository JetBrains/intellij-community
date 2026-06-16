import { GITHUB_URL, MARKETPLACE_URL, VENDOR } from '../data'

export default function Footer() {
  return (
    <footer className="footer">
      <div className="footer-inner">
        <a className="brand" href="#top">
          <img className="brand-logo" src="/icons/logo.svg" alt="" width={28} height={28} />
          <span>Agent Workbench</span>
        </a>
        <nav className="footer-links">
          <a href={MARKETPLACE_URL} target="_blank" rel="noreferrer">
            JetBrains Marketplace
          </a>
          <a href={GITHUB_URL} target="_blank" rel="noreferrer">
            GitHub
          </a>
        </nav>
        <p className="footer-meta">Built by {VENDOR}</p>
      </div>
    </footer>
  )
}
